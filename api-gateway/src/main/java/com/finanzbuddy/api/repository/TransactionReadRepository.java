package com.finanzbuddy.api.repository;

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
                "SELECT id, booking_date, value_date, amount, currency, counterparty, description,"
                        + " category FROM transactions WHERE document_id = ? ORDER BY id ASC",
                TransactionReadRepository::toTransaction,
                documentId);
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
                "SELECT DISTINCT t.category"
                        + baseHistoryQuery()
                        + " AND t.category IS NOT NULL AND TRIM(t.category) <> ''"
                        + " ORDER BY t.category",
                String.class,
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
        return " FROM transactions t JOIN documents d ON d.id = t.document_id"
                + " WHERE d.user_id = ? AND t.user_id = ? AND d.status = 'PROCESSED'";
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

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    public record DashboardTransactionMetrics(
            long savedTransactions, long uniqueCategories, long uniqueCounterparties) {}
}
