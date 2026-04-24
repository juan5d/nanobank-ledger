package com.nanobank.backend.repository;

import com.nanobank.backend.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByWalletId(Long walletId);
    List<Transaction> findByWalletIdAndCategory(Long walletId, String category);
    List<Transaction> findByWalletIdAndTimestampBetween(Long walletId, LocalDateTime start, LocalDateTime end);
}
