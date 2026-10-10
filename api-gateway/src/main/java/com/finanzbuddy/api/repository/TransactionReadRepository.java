package com.finanzbuddy.api.repository;

import com.finanzbuddy.api.document.dto.TransactionResponse;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
            where.append(" AND t.booking_date >= ?");
            parameters.add(Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND t.booking_date <= ?");
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
        List<TransactionResponse> content =
                jdbcTemplate.query(
                        "SELECT t.id, t.booking_date, t.value_date, t.amount, t.currency,"
                                + " t.counterparty, t.description, t.category"
                                + where
                                + " ORDER BY CASE WHEN t.booking_date IS NULL THEN 1 ELSE 0 END,"
                                + " t.booking_date DESC, t.id DESC LIMIT ? OFFSET ?",
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
}
