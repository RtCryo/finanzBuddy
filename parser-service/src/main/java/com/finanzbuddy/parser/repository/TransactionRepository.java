package com.finanzbuddy.parser.repository;

import com.finanzbuddy.parser.entity.Transaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByDocumentId(Long documentId);
}
