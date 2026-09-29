package com.finanzbuddy.api.repository;

import com.finanzbuddy.api.document.dto.TransactionResponse;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
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

    private final JdbcTemplate jdbcTemplate;

    public TransactionReadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TransactionResponse> findByDocumentId(Long documentId) {
        return jdbcTemplate.query(
                "SELECT id, booking_date, value_date, amount, currency, counterparty, description,"
                        + " category FROM transactions WHERE document_id = ? ORDER BY id ASC",
                (rs, rowNum) ->
                        new TransactionResponse(
                                rs.getLong("id"),
                                toLocalDate(rs.getDate("booking_date")),
                                toLocalDate(rs.getDate("value_date")),
                                rs.getBigDecimal("amount"),
                                rs.getString("currency"),
                                rs.getString("counterparty"),
                                rs.getString("description"),
                                rs.getString("category")),
                documentId);
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
