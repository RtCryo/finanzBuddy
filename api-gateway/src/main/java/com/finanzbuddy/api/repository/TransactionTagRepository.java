package com.finanzbuddy.api.repository;

import com.finanzbuddy.api.document.dto.TransactionTag;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionTagRepository {
    private final JdbcTemplate jdbc;

    public TransactionTagRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TransactionTag> findAll(long userId) {
        return jdbc.query(
                "SELECT id, name FROM personal_transaction_tags WHERE user_id = ? ORDER BY normalized_name, id",
                (rs, row) -> new TransactionTag(rs.getLong("id"), rs.getString("name")),
                userId);
    }

    public List<TransactionTag> findAssigned(long userId, long transactionId) {
        return jdbc.query(
                "SELECT p.id, p.name FROM personal_transaction_tags p JOIN transaction_tag_assignments a"
                        + " ON a.tag_id = p.id AND a.user_id = p.user_id WHERE a.user_id = ? AND a.transaction_id = ?"
                        + " ORDER BY p.normalized_name, p.id",
                (rs, row) -> new TransactionTag(rs.getLong("id"), rs.getString("name")),
                userId,
                transactionId);
    }

    public TransactionTag createOrFind(long userId, String name, String normalized) {
        List<TransactionTag> existing =
                jdbc.query(
                        "SELECT id, name FROM personal_transaction_tags WHERE user_id = ? AND normalized_name = ?",
                        (rs, row) -> new TransactionTag(rs.getLong("id"), rs.getString("name")),
                        userId,
                        normalized);
        if (!existing.isEmpty()) return existing.getFirst();
        jdbc.update(
                "INSERT INTO personal_transaction_tags (user_id, name, normalized_name) VALUES (?, ?, ?)",
                userId,
                name,
                normalized);
        return jdbc.queryForObject(
                "SELECT id, name FROM personal_transaction_tags WHERE user_id = ? AND normalized_name = ?",
                (rs, row) -> new TransactionTag(rs.getLong("id"), rs.getString("name")),
                userId,
                normalized);
    }

    public boolean ownedTagExists(long userId, long tagId) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM personal_transaction_tags WHERE user_id = ? AND id = ?",
                        Integer.class,
                        userId,
                        tagId);
        return count != null && count > 0;
    }

    /** Rows are {documentId, transactionId, tagId}; existing assignments are kept as they are. */
    public void ensureRuleAssignments(long userId, List<long[]> rows) {
        jdbc.batchUpdate(
                "INSERT INTO transaction_tag_assignments"
                        + " (user_id, document_id, transaction_id, tag_id, manual_assignment)"
                        + " SELECT CAST(? AS BIGINT), CAST(? AS BIGINT), CAST(? AS BIGINT),"
                        + " CAST(? AS BIGINT), FALSE WHERE NOT EXISTS"
                        + " (SELECT 1 FROM transaction_tag_assignments"
                        + " WHERE user_id = ? AND transaction_id = ? AND tag_id = ?)",
                rows,
                rows.size(),
                (statement, row) -> {
                    statement.setLong(1, userId);
                    statement.setLong(2, row[0]);
                    statement.setLong(3, row[1]);
                    statement.setLong(4, row[2]);
                    statement.setLong(5, userId);
                    statement.setLong(6, row[1]);
                    statement.setLong(7, row[2]);
                });
    }

    public void replace(
            long userId, long documentId, long transactionId, List<TransactionTag> tags) {
        var current =
                findAssigned(userId, transactionId).stream()
                        .map(TransactionTag::id)
                        .collect(java.util.stream.Collectors.toSet());
        var desired =
                tags.stream().map(TransactionTag::id).collect(java.util.stream.Collectors.toSet());
        for (Long tagId : current) {
            if (!desired.contains(tagId)) {
                jdbc.update(
                        "DELETE FROM transaction_tag_rule_contributions"
                                + " WHERE user_id = ? AND transaction_id = ? AND tag_id = ?",
                        userId,
                        transactionId,
                        tagId);
                jdbc.update(
                        "DELETE FROM transaction_tag_assignments"
                                + " WHERE user_id = ? AND transaction_id = ? AND tag_id = ?",
                        userId,
                        transactionId,
                        tagId);
            }
        }
        for (Long tagId : desired) {
            if (!current.contains(tagId)) {
                jdbc.update(
                        "INSERT INTO transaction_tag_assignments"
                                + " (user_id, document_id, transaction_id, tag_id, manual_assignment)"
                                + " VALUES (?, ?, ?, ?, TRUE)",
                        userId,
                        documentId,
                        transactionId,
                        tagId);
            }
        }
    }
}
