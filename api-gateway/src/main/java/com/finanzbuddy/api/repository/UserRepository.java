package com.finanzbuddy.api.repository;

import com.finanzbuddy.api.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByAccountId(UUID accountId);

    boolean existsByEmailIgnoreCaseAndAccountIdIsNull(String email);
}
