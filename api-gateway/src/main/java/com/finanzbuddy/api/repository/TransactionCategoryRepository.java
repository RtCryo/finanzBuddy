package com.finanzbuddy.api.repository;

import com.finanzbuddy.api.document.dto.TransactionResponse;
import com.finanzbuddy.api.document.dto.TransactionVersionResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionCategoryRepository {

    private final JdbcTemplate jdbcTemplate;

    public TransactionCategoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TransactionSnapshot findOwnedTransaction(long userId, long transactionId) {
        List<TransactionSnapshot> rows =
                jdbcTemplate.query(
                        "SELECT t.id, t.document_id, t.user_id, t.booking_date, t.value_date,"
                                + " t.amount, t.currency, t.counterparty, t.description, t.category"
                                + " FROM transactions t JOIN documents d ON d.id = t.document_id"
                                + " WHERE t.id = ? AND t.user_id = ? AND d.user_id = ?",
                        (rs, rowNum) ->
                                new TransactionSnapshot(
                                        rs.getLong("id"),
                                        rs.getLong("document_id"),
                                        rs.getLong("user_id"),
                                        new TransactionResponse(
                                                rs.getLong("id"),
                                                TransactionReadRepository.toLocalDate(
                                                        rs.getDate("booking_date")),
                                                TransactionReadRepository.toLocalDate(
                                                        rs.getDate("value_date")),
                                                rs.getBigDecimal("amount"),
                                                rs.getString("currency"),
                                                rs.getString("counterparty"),
                                                rs.getString("description"),
                                                rs.getString("category"))),
                        transactionId,
                        userId,
                        userId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public void lockDocument(long userId, long documentId) {
        jdbcTemplate.queryForObject("SELECT id FROM documents WHERE id = ? AND user_id = ? FOR UPDATE",
                Long.class, documentId, userId);
    }

    public List<TransactionVersionResponse> findVersions(long userId, long transactionId) {
        return jdbcTemplate.query(
                "SELECT version_number, booking_date, value_date, amount, currency, counterparty,"
                        + " description, category, created_at FROM transaction_versions"
                        + " WHERE transaction_id = ? AND user_id = ? ORDER BY version_number ASC",
                (rs, rowNum) ->
                        new TransactionVersionResponse(
                                rs.getInt("version_number"),
                                new TransactionResponse(
                                        transactionId,
                                        TransactionReadRepository.toLocalDate(
                                                rs.getDate("booking_date")),
                                        TransactionReadRepository.toLocalDate(
                                                rs.getDate("value_date")),
                                        rs.getBigDecimal("amount"),
                                        rs.getString("currency"),
                                        rs.getString("counterparty"),
                                        rs.getString("description"),
                                        rs.getString("category")),
                                toInstant(rs.getTimestamp("created_at"))),
                transactionId,
                userId);
    }

    public List<String> findAvailableCategories(long userId) {
        TreeSet<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        categories.addAll(
                jdbcTemplate.queryForList(
                        "SELECT DISTINCT t.category FROM transactions t"
                                + " JOIN documents d ON d.id = t.document_id"
                                + " WHERE d.user_id = ? AND t.user_id = ?"
                                + " AND t.category IS NOT NULL AND TRIM(t.category) <> ''",
                        String.class,
                        userId,
                        userId));
        categories.addAll(
                jdbcTemplate.queryForList(
                        "SELECT DISTINCT category FROM transaction_versions WHERE user_id = ?"
                                + " AND category IS NOT NULL AND TRIM(category) <> ''",
                        String.class,
                        userId));
        categories.addAll(
                jdbcTemplate.queryForList(
                        "SELECT name FROM personal_transaction_categories WHERE user_id = ?",
                        String.class,
                        userId));
        return new ArrayList<>(categories);
    }

    public String findPersonalCategory(long userId, String normalizedName) {
        List<String> names =
                jdbcTemplate.queryForList(
                        "SELECT name FROM personal_transaction_categories"
                                + " WHERE user_id = ? AND normalized_name = ?",
                        String.class,
                        userId,
                        normalizedName);
        return names.isEmpty() ? null : names.getFirst();
    }

    public String createPersonalCategory(long userId, String name, String normalizedName) {
        String existing = findPersonalCategory(userId, normalizedName);
        if (existing != null) return existing;
        try {
            jdbcTemplate.update(
                    "INSERT INTO personal_transaction_categories (user_id, name, normalized_name)"
                            + " VALUES (?, ?, ?)",
                    userId,
                    name,
                    normalizedName);
            return name;
        } catch (DuplicateKeyException exception) {
            throw exception;
        }
    }

    public void appendVersion(
            long userId, long documentId, int versionNumber, TransactionResponse transaction) {
        jdbcTemplate.update(
                "INSERT INTO transaction_versions (transaction_id, document_id, user_id,"
                        + " version_number, booking_date, value_date, amount, currency, counterparty,"
                        + " description, category) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                transaction.id(),
                documentId,
                userId,
                versionNumber,
                transaction.bookingDate(),
                transaction.valueDate(),
                transaction.amount(),
                transaction.currency(),
                transaction.counterparty(),
                transaction.description(),
                transaction.category());
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record TransactionSnapshot(
            long transactionId, long documentId, long userId, TransactionResponse transaction) {}
}
