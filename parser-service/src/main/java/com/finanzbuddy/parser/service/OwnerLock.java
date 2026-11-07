package com.finanzbuddy.parser.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Serializes work per owner with a transaction-scoped PostgreSQL advisory lock. The lock is
 * released automatically when the surrounding transaction commits or rolls back. On other databases
 * (the in-memory test database) it does nothing.
 */
@Component
public class OwnerLock {

    private static final long NAMESPACE = 0x70617273L << 32;

    @PersistenceContext private EntityManager entityManager;

    private final boolean postgres;

    public OwnerLock(DataSource dataSource) {
        this.postgres = isPostgres(dataSource);
    }

    public void acquire(Long userId) {
        if (!postgres) {
            return;
        }
        long key = NAMESPACE | (userId & 0xFFFFFFFFL);
        entityManager
                .createNativeQuery("select pg_advisory_xact_lock(:key)")
                .setParameter("key", key)
                .getSingleResult();
    }

    private static boolean isPostgres(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot determine database product", e);
        }
    }
}
