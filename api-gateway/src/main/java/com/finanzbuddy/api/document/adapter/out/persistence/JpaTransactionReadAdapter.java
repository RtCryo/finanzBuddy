package com.finanzbuddy.api.document.adapter.out.persistence;

import com.finanzbuddy.api.document.domain.model.Transaction;
import com.finanzbuddy.api.document.port.out.TransactionReadPort;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-only view of the {@code transactions} table, which is owned and written by the
 * parser-service. A JDBC query (rather than a JPA entity) is used deliberately so the API Gateway
 * does not take a Hibernate {@code validate} dependency on a table another service creates — the
 * gateway starts before the parser-service, so the table may not yet exist at gateway boot.
 */
@Component
public class JpaTransactionReadAdapter implements TransactionReadPort {

    private final JdbcTemplate jdbcTemplate;

    public JpaTransactionReadAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Transaction> findByDocumentId(long documentId) {
        return jdbcTemplate.query(
                "SELECT id, booking_date, value_date, amount, currency, counterparty, description,"
                        + " category FROM transactions WHERE document_id = ? ORDER BY id ASC",
                (rs, rowNum) ->
                        new Transaction(
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
