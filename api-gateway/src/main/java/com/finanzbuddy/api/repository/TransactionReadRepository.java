package com.finanzbuddy.api.repository;

import com.finanzbuddy.api.document.dto.RelatedExpenseResponse;
import com.finanzbuddy.api.document.dto.TransactionResponse;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only view of the {@code transactions} table, which is owned and written by the
 * parser-service. A JDBC query (rather than a JPA entity) is used deliberately so the API Gateway
 * does not take a Hibernate {@code validate} dependency on a table another service creates — the
 * gateway starts before the parser-service, so the table may not yet exist at gateway boot.
 */
@Repository
public class TransactionReadRepository {

    public static final String UNCATEGORIZED = "__uncategorized__";
    private static final Map<String, String> HISTORY_SORT_COLUMNS =
            Map.of(
                    "valueDate", "t.value_date",
                    "amount", "t.amount",
                    "currency", "t.currency",
                    "counterparty", "t.counterparty",
                    "description", "t.description",
                    "category", "t.category");

    private final JdbcTemplate jdbcTemplate;

    public TransactionReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TransactionResponse> findByDocumentId(Long documentId) {
        return jdbcTemplate.query(
                "SELECT t.id, t.booking_date, t.value_date, t.amount, t.currency, t.counterparty,"
                        + " t.description, t.category"
                        + currentTransactionsQuery()
                        + " t WHERE t.document_id = ? ORDER BY t.id ASC",
                TransactionReadRepository::toTransaction,
                documentId);
    }

    public java.util.Optional<TransactionResponse> findOwnedProcessedSource(Long userId, Long id) {
        return jdbcTemplate
                .query(
                        "SELECT t.id, t.booking_date, t.value_date, t.amount, t.currency,"
                                + " t.counterparty, t.description, t.category"
                                + baseHistoryQuery()
                                + " AND t.id = ?",
                        TransactionReadRepository::toTransaction,
                        userId,
                        userId,
                        id)
                .stream()
                .findFirst();
    }

    public Page<RelatedExpenseResponse> findRelatedExpenses(
            Long userId, Long sourceId, String counterparty, Pageable pageable) {
        if (counterparty == null || counterparty.isBlank()) return Page.empty(pageable);
        String where =
                baseHistoryQuery() + " AND t.id <> ? AND t.amount < 0 AND t.counterparty = ?";
        Long total =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*)" + where,
                        Long.class,
                        userId,
                        userId,
                        sourceId,
                        counterparty);
        var rows =
                jdbcTemplate.query(
                        "SELECT t.id, t.value_date, t.amount, t.currency"
                                + where
                                + " ORDER BY CASE WHEN t.value_date IS NULL THEN 1 ELSE 0 END,"
                                + " t.value_date DESC, t.id ASC LIMIT ? OFFSET ?",
                        (rs, rowNum) ->
                                new RelatedExpenseResponse(
                                        rs.getLong("id"), toLocalDate(rs.getDate("value_date")),
                                        rs.getBigDecimal("amount"), rs.getString("currency")),
                        userId,
                        userId,
                        sourceId,
                        counterparty,
                        pageable.getPageSize(),
                        pageable.getOffset());
        return new PageImpl<>(rows, pageable, total == null ? 0 : total);
    }

    public Page<TransactionResponse> findHistory(
            Long userId,
            String query,
            LocalDate from,
            LocalDate to,
            String category,
            String sortField,
            String sortDirection,
            Pageable pageable) {
        return findHistory(
                userId,
                query,
                from,
                to,
                category,
                sortField,
                sortDirection,
                pageable,
                List.of(),
                "AND");
    }

    public Page<TransactionResponse> findHistory(
            Long userId,
            String query,
            LocalDate from,
            LocalDate to,
            String category,
            String sortField,
            String sortDirection,
            Pageable pageable,
            List<Long> selectedTagIds,
            String tagMode) {
        if (!List.of("AND", "OR").contains(tagMode))
            throw new IllegalArgumentException("Unsupported tag mode");
        List<Long> tagIds = selectedTagIds.stream().distinct().toList();
        StringBuilder where = new StringBuilder(baseHistoryQuery());
        List<Object> parameters = new ArrayList<>();
        parameters.add(userId);
        parameters.add(userId);

        if (query != null && !query.isBlank()) {
            where.append(
                    " AND (LOWER(COALESCE(t.description, '')) LIKE ?"
                            + " OR LOWER(COALESCE(t.counterparty, '')) LIKE ?)");
            String text = "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            parameters.add(text);
            parameters.add(text);
        }
        if (from != null) {
            where.append(" AND t.value_date >= ?");
            parameters.add(Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND t.value_date <= ?");
            parameters.add(Date.valueOf(to));
        }
        if (category != null && !category.isBlank()) {
            if (UNCATEGORIZED.equals(category)) {
                where.append(" AND (t.category IS NULL OR TRIM(t.category) = '')");
            } else {
                where.append(" AND t.category = ?");
                parameters.add(category);
            }
        }

        if (!tagIds.isEmpty()) {
            String placeholders =
                    String.join(",", java.util.Collections.nCopies(tagIds.size(), "?"));
            where.append(
                    " AND (SELECT COUNT(DISTINCT a.tag_id) FROM transaction_tag_assignments a"
                            + " WHERE a.user_id = t.user_id AND a.transaction_id = t.id"
                            + " AND a.document_id = t.document_id AND a.tag_id IN ("
                            + placeholders
                            + "))");
            parameters.addAll(tagIds);
            if (tagMode.equals("AND")) {
                where.append(" = ?");
                parameters.add(tagIds.size());
            } else where.append(" > 0");
        }

        Long total =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*)" + where, Long.class, parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(pageable.getPageSize());
        pageParameters.add(pageable.getOffset());
        String sortColumn = HISTORY_SORT_COLUMNS.get(sortField);
        if (sortColumn == null) {
            throw new IllegalArgumentException("Unsupported transaction sort field");
        }
        String direction =
                switch (sortDirection) {
                    case "ASC" -> "ASC";
                    case "DESC" -> "DESC";
                    default ->
                            throw new IllegalArgumentException(
                                    "Unsupported transaction sort direction");
                };
        List<TransactionResponse> content =
                jdbcTemplate.query(
                        "SELECT t.id, t.booking_date, t.value_date, t.amount, t.currency,"
                                + " t.counterparty, t.description, t.category"
                                + where
                                + " ORDER BY CASE WHEN "
                                + sortColumn
                                + " IS NULL THEN 1 ELSE 0 END,"
                                + " "
                                + sortColumn
                                + " "
                                + direction
                                + ", t.id ASC LIMIT ? OFFSET ?",
                        TransactionReadRepository::toTransaction,
                        pageParameters.toArray());
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    public List<String> findHistoryCategories(Long userId) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT category FROM ("
                        + "SELECT t.category AS category"
                        + baseHistoryQuery()
                        + " AND t.category IS NOT NULL AND TRIM(t.category) <> ''"
                        + " UNION SELECT v.category AS category FROM transaction_versions v"
                        + " JOIN documents d ON d.id = v.document_id"
                        + " WHERE d.user_id = ? AND v.user_id = ? AND d.status = 'PROCESSED'"
                        + " AND v.category IS NOT NULL AND TRIM(v.category) <> ''"
                        + " UNION SELECT name AS category FROM personal_transaction_categories"
                        + " WHERE user_id = ?) available_categories ORDER BY category",
                String.class,
                userId,
                userId,
                userId,
                userId,
                userId);
    }

    public boolean historyHasUncategorized(Long userId) {
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*)"
                                + baseHistoryQuery()
                                + " AND (t.category IS NULL OR TRIM(t.category) = '')",
                        Long.class,
                        userId,
                        userId);
        return count != null && count > 0;
    }

    public DashboardTransactionMetrics findDashboardMetrics(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) AS saved_transactions,"
                        + " COUNT(DISTINCT CASE WHEN t.category IS NOT NULL"
                        + " AND TRIM(t.category) <> '' THEN t.category END) AS unique_categories,"
                        + " COUNT(DISTINCT CASE WHEN t.counterparty IS NOT NULL"
                        + " AND TRIM(t.counterparty) <> '' THEN t.counterparty END)"
                        + " AS unique_counterparties"
                        + baseHistoryQuery(),
                (rs, rowNum) ->
                        new DashboardTransactionMetrics(
                                rs.getLong("saved_transactions"),
                                rs.getLong("unique_categories"),
                                rs.getLong("unique_counterparties")),
                userId,
                userId);
    }

    private static String baseHistoryQuery() {
        return " FROM ("
                + currentTransactionsSelect()
                + ") t JOIN documents d ON d.id = t.document_id"
                + " WHERE d.user_id = ? AND t.user_id = ? AND d.status = 'PROCESSED'";
    }

    private static String currentTransactionsQuery() {
        return " FROM (" + currentTransactionsSelect() + ")";
    }

    private static String currentTransactionsSelect() {
        return "SELECT t.id, t.document_id, t.user_id,"
                + " CASE WHEN v.id IS NULL THEN t.booking_date ELSE v.booking_date END AS booking_date,"
                + " CASE WHEN v.id IS NULL THEN t.value_date ELSE v.value_date END AS value_date,"
                + " CASE WHEN v.id IS NULL THEN t.amount ELSE v.amount END AS amount,"
                + " CASE WHEN v.id IS NULL THEN t.currency ELSE v.currency END AS currency,"
                + " CASE WHEN v.id IS NULL THEN t.counterparty ELSE v.counterparty END AS counterparty,"
                + " CASE WHEN v.id IS NULL THEN t.description ELSE v.description END AS description,"
                + " CASE WHEN v.id IS NULL THEN t.category ELSE v.category END AS category"
                + " FROM transactions t LEFT JOIN transaction_versions v"
                + " ON v.transaction_id = t.id AND v.document_id = t.document_id"
                + " AND v.user_id = t.user_id AND v.version_number = ("
                + "SELECT MAX(latest.version_number) FROM transaction_versions latest"
                + " WHERE latest.transaction_id = t.id AND latest.document_id = t.document_id"
                + " AND latest.user_id = t.user_id)";
    }

    private static TransactionResponse toTransaction(java.sql.ResultSet rs, int rowNum)
            throws java.sql.SQLException {
        return new TransactionResponse(
                rs.getLong("id"),
                toLocalDate(rs.getDate("booking_date")),
                toLocalDate(rs.getDate("value_date")),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("counterparty"),
                rs.getString("description"),
                rs.getString("category"));
    }

    static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    public record DashboardTransactionMetrics(
            long savedTransactions, long uniqueCategories, long uniqueCounterparties) {}
}
