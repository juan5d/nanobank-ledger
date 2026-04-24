package com.nanobank.backend.service;

import com.nanobank.backend.dto.MoveTransactionRequest;
import com.nanobank.backend.dto.TransactionRequest;
import com.nanobank.backend.dto.TransactionResponse;
import com.nanobank.backend.dto.TransferRequest;
import com.nanobank.backend.entity.Transaction;
import com.nanobank.backend.entity.TransactionType;
import com.nanobank.backend.entity.User;
import com.nanobank.backend.entity.Wallet;
import com.nanobank.backend.exception.InsufficientFundsException;
import com.nanobank.backend.exception.ResourceNotFoundException;
import com.nanobank.backend.repository.TransactionRepository;
import com.nanobank.backend.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private TransactionService transactionService;

    private User user;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        user = new User("alice", "alice@test.com", "encoded");
        user.setId(1L);
        wallet = new Wallet("Savings", user);
        wallet.setId(1L);
        wallet.setBalance(new BigDecimal("500.00"));
    }

    @Test
    void create_income_increasesWalletBalance() {
        TransactionRequest req = new TransactionRequest("Salary", new BigDecimal("1000"), "INCOME", "SALARY", 1L);
        Transaction saved = new Transaction(new BigDecimal("1000"), TransactionType.INCOME, "Salary", "SALARY", wallet);
        saved.setId(1L);

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenReturn(wallet);
        when(transactionRepository.save(any())).thenReturn(saved);

        TransactionResponse response = transactionService.create(req, "alice@test.com");

        assertThat(wallet.getBalance()).isEqualByComparingTo("1500.00");
        assertThat(response.type()).isEqualTo("INCOME");
    }

    @Test
    void create_expense_decreasesWalletBalance() {
        TransactionRequest req = new TransactionRequest("Rent", new BigDecimal("200"), "EXPENSE", "HOUSING", 1L);
        Transaction saved = new Transaction(new BigDecimal("200"), TransactionType.EXPENSE, "Rent", "HOUSING", wallet);
        saved.setId(2L);

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any())).thenReturn(wallet);
        when(transactionRepository.save(any())).thenReturn(saved);

        transactionService.create(req, "alice@test.com");

        assertThat(wallet.getBalance()).isEqualByComparingTo("300.00");
    }

    @Test
    void create_expense_insufficientFunds_throwsInsufficientFundsException() {
        TransactionRequest req = new TransactionRequest("Too much", new BigDecimal("1000"), "EXPENSE", "X", 1L);

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> transactionService.create(req, "alice@test.com"))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void create_walletNotFound_throwsResourceNotFoundException() {
        when(walletRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.create(
                new TransactionRequest("X", BigDecimal.ONE, "INCOME", null, 99L), "alice@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_invalidType_throwsIllegalArgumentException() {
        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> transactionService.create(
                new TransactionRequest("X", BigDecimal.ONE, "INVALID", null, 1L), "alice@test.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_walletFromAnotherUser_throwsResourceNotFoundException() {
        User otherUser = new User("bob", "bob@test.com", "encoded");
        Wallet otherWallet = new Wallet("Bob wallet", otherUser);
        otherWallet.setId(2L);

        when(walletRepository.findById(2L)).thenReturn(Optional.of(otherWallet));

        assertThatThrownBy(() -> transactionService.create(
                new TransactionRequest("X", BigDecimal.ONE, "INCOME", null, 2L), "alice@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void transfer_movesBalanceBetweenWallets() {
        Wallet target = new Wallet("Expenses", user);
        target.setId(2L);
        target.setBalance(BigDecimal.ZERO);

        TransferRequest req = new TransferRequest(1L, 2L, new BigDecimal("100"), "move");

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.findById(2L)).thenReturn(Optional.of(target));
        when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            t.setId(10L);
            return t;
        });

        transactionService.transfer(req, "alice@test.com");

        assertThat(wallet.getBalance()).isEqualByComparingTo("400.00");
        assertThat(target.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void transfer_insufficientFunds_throwsInsufficientFundsException() {
        Wallet target = new Wallet("Expenses", user);
        target.setId(2L);
        target.setBalance(BigDecimal.ZERO);

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> transactionService.transfer(
                new TransferRequest(1L, 2L, new BigDecimal("9999"), null), "alice@test.com"))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void transfer_targetWalletFromAnotherUser_throwsResourceNotFoundException() {
        User otherUser = new User("bob", "bob@test.com", "encoded");
        Wallet otherWallet = new Wallet("Bob wallet", otherUser);
        otherWallet.setId(2L);

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(walletRepository.findById(2L)).thenReturn(Optional.of(otherWallet));

        assertThatThrownBy(() -> transactionService.transfer(
                new TransferRequest(1L, 2L, new BigDecimal("100"), null), "alice@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void move_incomeTransaction_reassignsWalletAndBalances() {
        Wallet target = new Wallet("Expenses", user);
        target.setId(2L);
        target.setBalance(new BigDecimal("25.00"));

        Transaction transaction = new Transaction(new BigDecimal("100.00"), TransactionType.INCOME, "Salary", "SALARY", wallet);
        transaction.setId(99L);

        when(transactionRepository.findById(99L)).thenReturn(Optional.of(transaction));
        when(walletRepository.findById(2L)).thenReturn(Optional.of(target));
        when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TransactionResponse response = transactionService.move(new MoveTransactionRequest(99L, 2L), "alice@test.com");

        assertThat(wallet.getBalance()).isEqualByComparingTo("400.00");
        assertThat(target.getBalance()).isEqualByComparingTo("125.00");
        assertThat(response.walletId()).isEqualTo(2L);
    }

    @Test
    void move_expenseTransaction_requiresFundsInTargetWallet() {
        Wallet source = new Wallet("Savings", user);
        source.setId(1L);
        source.setBalance(new BigDecimal("450.00"));

        Wallet target = new Wallet("Expenses", user);
        target.setId(2L);
        target.setBalance(new BigDecimal("10.00"));

        Transaction transaction = new Transaction(new BigDecimal("50.00"), TransactionType.EXPENSE, "Groceries", "FOOD", source);
        transaction.setId(77L);

        when(transactionRepository.findById(77L)).thenReturn(Optional.of(transaction));
        when(walletRepository.findById(2L)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> transactionService.move(new MoveTransactionRequest(77L, 2L), "alice@test.com"))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void move_transactionFromAnotherUser_throwsResourceNotFoundException() {
        User otherUser = new User("bob", "bob@test.com", "encoded");
        Wallet otherWallet = new Wallet("Bob wallet", otherUser);
        otherWallet.setId(3L);

        Transaction otherTransaction = new Transaction(BigDecimal.TEN, TransactionType.INCOME, "Bonus", "OTHER", otherWallet);
        otherTransaction.setId(88L);

        when(transactionRepository.findById(88L)).thenReturn(Optional.of(otherTransaction));

        assertThatThrownBy(() -> transactionService.move(new MoveTransactionRequest(88L, 1L), "alice@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByFilters_withCategory_returnsFilteredResults() {
        Transaction t = new Transaction(BigDecimal.TEN, TransactionType.EXPENSE, "Food", "FOOD", wallet);
        t.setId(1L);

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletIdAndCategory(1L, "FOOD")).thenReturn(List.of(t));

        List<TransactionResponse> result = transactionService.findByFilters(1L, "FOOD", null, null, "alice@test.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).category()).isEqualTo("FOOD");
    }

    @Test
    void findByFilters_noFilter_returnsAll() {
        Transaction t1 = new Transaction(BigDecimal.TEN, TransactionType.INCOME, "A", "X", wallet);
        t1.setId(1L);
        Transaction t2 = new Transaction(BigDecimal.ONE, TransactionType.EXPENSE, "B", "Y", wallet);
        t2.setId(2L);

        when(walletRepository.findById(1L)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletId(1L)).thenReturn(List.of(t1, t2));

        List<TransactionResponse> result = transactionService.findByFilters(1L, null, null, null, "alice@test.com");

        assertThat(result).hasSize(2);
    }

    @Test
    void findByFilters_walletFromAnotherUser_throwsResourceNotFoundException() {
        User otherUser = new User("bob", "bob@test.com", "encoded");
        Wallet otherWallet = new Wallet("Bob wallet", otherUser);
        otherWallet.setId(4L);

        when(walletRepository.findById(4L)).thenReturn(Optional.of(otherWallet));

        assertThatThrownBy(() -> transactionService.findByFilters(4L, null, null, null, "alice@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
