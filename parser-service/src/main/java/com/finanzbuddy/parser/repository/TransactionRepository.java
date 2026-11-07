package com.finanzbuddy.parser.repository;

import com.finanzbuddy.parser.entity.Transaction;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByDocumentId(Long documentId);

    /** Key columns of an owner's transactions booked within the range or without booking date. */
    @Query(
            """
            select new com.finanzbuddy.parser.repository.StoredTransactionKey(
                t.bookingDate, t.valueDate, t.amount, t.currency, t.counterparty, t.description)
            from Transaction t
            where t.userId = :userId
              and (t.bookingDate is null
                   or (t.bookingDate >= :from and t.bookingDate <= :to))
            """)
    List<StoredTransactionKey> findKeysInBookingRange(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Key columns of an owner's transactions that have no booking date. */
    @Query(
            """
            select new com.finanzbuddy.parser.repository.StoredTransactionKey(
                t.bookingDate, t.valueDate, t.amount, t.currency, t.counterparty, t.description)
            from Transaction t
            where t.userId = :userId and t.bookingDate is null
            """)
    List<StoredTransactionKey> findKeysWithoutBookingDate(@Param("userId") Long userId);
}
