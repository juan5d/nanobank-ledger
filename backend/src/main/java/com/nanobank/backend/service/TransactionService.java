package com.nanobank.backend.service;

import com.nanobank.backend.dto.TransactionRequest;
import com.nanobank.backend.dto.TransactionResponse;
import com.nanobank.backend.dto.TransferRequest;
import com.nanobank.backend.entity.Transaction;
import com.nanobank.backend.entity.TransactionType;
import com.nanobank.backend.entity.Wallet;
import com.nanobank.backend.exception.InsufficientFundsException;
import com.nanobank.backend.exception.ResourceNotFoundException;
import com.nanobank.backend.repository.TransactionRepository;
import com.nanobank.backend.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    public TransactionService(TransactionRepository transactionRepository,
            WalletRepository walletRepository) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
    }

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        Wallet wallet = walletRepository.findById(request.walletId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + request.walletId()));
        TransactionType type = parseType(request.type());
        applyBalance(wallet, request.amount(), type);
        Transaction tx = new Transaction(request.amount(), type, request.description(),
                request.category(), wallet);
        walletRepository.save(wallet);
        return toResponse(transactionRepository.save(tx));
    }

    @Transactional
    public TransactionResponse transfer(TransferRequest request) {
        Wallet from = walletRepository.findById(request.fromWalletId())
                .orElseThrow(() -> new ResourceNotFoundException("Source wallet not found"));
        Wallet to = walletRepository.findById(request.toWalletId())
                .orElseThrow(() -> new ResourceNotFoundException("Target wallet not found"));

        if (from.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException("Insufficient funds in wallet: " + from.getName());
        }

        from.setBalance(from.getBalance().subtract(request.amount()));
        to.setBalance(to.getBalance().add(request.amount()));

        String desc = request.description() != null ? request.description() : "";
        Transaction debit = new Transaction(request.amount(), TransactionType.EXPENSE,
                desc.isBlank() ? "Transfer to " + to.getName() : desc, "TRANSFER", from);
        Transaction credit = new Transaction(request.amount(), TransactionType.INCOME,
                desc.isBlank() ? "Transfer from " + from.getName() : desc, "TRANSFER", to);

        walletRepository.save(from);
        walletRepository.save(to);
        transactionRepository.save(debit);
        return toResponse(transactionRepository.save(credit));
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> findByFilters(Long walletId, String category,
            LocalDateTime start, LocalDateTime end) {
        List<Transaction> results;
        if (category != null && !category.isBlank()) {
            results = transactionRepository.findByWalletIdAndCategory(walletId, category);
        } else if (start != null && end != null) {
            results = transactionRepository.findByWalletIdAndTimestampBetween(walletId, start, end);
        } else {
            results = transactionRepository.findByWalletId(walletId);
        }
        return results.stream().map(this::toResponse).toList();
    }

    private void applyBalance(Wallet wallet, BigDecimal amount, TransactionType type) {
        if (type == TransactionType.INCOME) {
            wallet.setBalance(wallet.getBalance().add(amount));
        } else {
            if (wallet.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException("Insufficient funds in wallet: " + wallet.getName());
            }
            wallet.setBalance(wallet.getBalance().subtract(amount));
        }
    }

    private TransactionType parseType(String type) {
        try {
            return TransactionType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid transaction type: " + type);
        }
    }

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
                t.getId(), t.getDescription(), t.getAmount(),
                t.getType().name(), t.getCategory(), t.getTimestamp(),
                t.getWallet().getId());
    }
}
