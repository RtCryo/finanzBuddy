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

    public void ensureRuleAssignment(long userId, long documentId, long transactionId, long tagId) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM transaction_tag_assignments"
                                + " WHERE user_id = ? AND transaction_id = ? AND tag_id = ?",
                        Integer.class,
                        userId,
                        transactionId,
                        tagId);
        if (count == null || count == 0) {
            jdbc.update(
                    "INSERT INTO transaction_tag_assignments"
                            + " (user_id, document_id, transaction_id, tag_id, manual_assignment)"
                            + " VALUES (?, ?, ?, ?, FALSE)",
                    userId,
                    documentId,
                    transactionId,
                    tagId);
        }
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
