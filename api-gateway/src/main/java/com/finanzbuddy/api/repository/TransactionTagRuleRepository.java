package com.finanzbuddy.api.repository;

import com.finanzbuddy.api.document.dto.TransactionTagRule;
import com.finanzbuddy.api.document.dto.TransactionTagRule.Condition;
import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class TransactionTagRuleRepository {
    private final JdbcTemplate jdbc;

    public TransactionTagRuleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockOwner(long userId) {
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", Long.class, userId);
    }

    public List<TransactionTagRule> findAll(long userId) {
        return jdbc.query(
                "SELECT id, tag_id, mode FROM transaction_tag_rules WHERE user_id = ? ORDER BY id",
                (rs, row) ->
                        new TransactionTagRule(
                                rs.getLong("id"),
                                rs.getLong("tag_id"),
                                rs.getString("mode"),
                                findConditions(userId, rs.getLong("id"))),
                userId);
    }

    public TransactionTagRule find(long userId, long id) {
        return findAll(userId).stream().filter(rule -> rule.id() == id).findFirst().orElse(null);
    }

    private List<Condition> findConditions(long userId, long ruleId) {
        return jdbc.query(
                "SELECT field_name, operator, match_value FROM transaction_tag_rule_conditions"
                        + " WHERE user_id = ? AND rule_id = ? ORDER BY position",
                (rs, row) ->
                        new Condition(
                                rs.getString("field_name"),
                                rs.getString("operator"),
                                rs.getString("match_value")),
                userId,
                ruleId);
    }

    public TransactionTagRule create(
            long userId, long tagId, String mode, List<Condition> conditions) {
        var key = new GeneratedKeyHolder();
        jdbc.update(
                connection -> {
                    PreparedStatement statement =
                            connection.prepareStatement(
                                    "INSERT INTO transaction_tag_rules (user_id, tag_id, mode) VALUES (?, ?, ?)",
                                    new String[] {"id"});
                    statement.setLong(1, userId);
                    statement.setLong(2, tagId);
                    statement.setString(3, mode);
                    return statement;
                },
                key);
        long id = key.getKey().longValue();
        insertConditions(userId, id, conditions);
        return new TransactionTagRule(id, tagId, mode, conditions);
    }

    public TransactionTagRule update(
            long userId, long id, long tagId, String mode, List<Condition> conditions) {
        if (jdbc.update(
                        "UPDATE transaction_tag_rules SET tag_id = ?, mode = ? WHERE user_id = ? AND id = ?",
                        tagId,
                        mode,
                        userId,
                        id)
                == 0) return null;
        jdbc.update(
                "DELETE FROM transaction_tag_rule_conditions WHERE user_id = ? AND rule_id = ?",
                userId,
                id);
        insertConditions(userId, id, conditions);
        return new TransactionTagRule(id, tagId, mode, conditions);
    }

    private void insertConditions(long userId, long ruleId, List<Condition> conditions) {
        for (int position = 0; position < conditions.size(); position++) {
            Condition condition = conditions.get(position);
            jdbc.update(
                    "INSERT INTO transaction_tag_rule_conditions"
                            + " (user_id, rule_id, position, field_name, operator, match_value)"
                            + " VALUES (?, ?, ?, ?, ?, ?)",
                    userId,
                    ruleId,
                    position,
                    condition.field(),
                    condition.operator(),
                    condition.value());
        }
    }

    public void delete(long userId, long id) {
        jdbc.update("DELETE FROM transaction_tag_rules WHERE user_id = ? AND id = ?", userId, id);
    }

    public void addContribution(
            long userId, long ruleId, long documentId, long transactionId, long tagId) {
        jdbc.update(
                "INSERT INTO transaction_tag_rule_contributions"
                        + " (user_id, rule_id, document_id, transaction_id, tag_id)"
                        + " VALUES (?, ?, ?, ?, ?)",
                userId,
                ruleId,
                documentId,
                transactionId,
                tagId);
    }

    public void deleteContributions(long userId, long ruleId) {
        jdbc.update(
                "DELETE FROM transaction_tag_rule_contributions WHERE user_id = ? AND rule_id = ?",
                userId,
                ruleId);
    }

    public void deleteContributions(long userId, long transactionId, long tagId) {
        jdbc.update(
                "DELETE FROM transaction_tag_rule_contributions"
                        + " WHERE user_id = ? AND transaction_id = ? AND tag_id = ?",
                userId,
                transactionId,
                tagId);
    }

    public void promoteContributions(long userId, long ruleId) {
        jdbc.update(
                "UPDATE transaction_tag_assignments SET manual_assignment = TRUE"
                        + " WHERE user_id = ? AND EXISTS (SELECT 1 FROM transaction_tag_rule_contributions c"
                        + " WHERE c.user_id = transaction_tag_assignments.user_id"
                        + " AND c.transaction_id = transaction_tag_assignments.transaction_id"
                        + " AND c.tag_id = transaction_tag_assignments.tag_id AND c.rule_id = ?)",
                userId,
                ruleId);
    }

    public void pruneAssignments(long userId) {
        jdbc.update(
                "DELETE FROM transaction_tag_assignments WHERE user_id = ?"
                        + " AND manual_assignment = FALSE AND NOT EXISTS"
                        + " (SELECT 1 FROM transaction_tag_rule_contributions c"
                        + " WHERE c.user_id = transaction_tag_assignments.user_id"
                        + " AND c.transaction_id = transaction_tag_assignments.transaction_id"
                        + " AND c.tag_id = transaction_tag_assignments.tag_id)",
                userId);
    }
}
