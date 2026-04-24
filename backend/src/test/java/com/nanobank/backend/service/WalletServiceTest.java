package com.nanobank.backend.service;

import com.nanobank.backend.dto.WalletRequest;
import com.nanobank.backend.dto.WalletResponse;
import com.nanobank.backend.entity.User;
import com.nanobank.backend.entity.Wallet;
import com.nanobank.backend.exception.ResourceNotFoundException;
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
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private WalletService walletService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("alice", "alice@test.com", "encoded");
        user.setId(1L);
    }

    @Test
    void create_whenUserExists_returnsWalletResponse() {
        WalletRequest request = new WalletRequest("Savings");
        Wallet saved = new Wallet("Savings", user);
        saved.setId(10L);

        when(userService.findByEmail("alice@test.com")).thenReturn(user);
        when(walletRepository.save(any())).thenReturn(saved);

        WalletResponse response = walletService.create(request, "alice@test.com");

        assertThat(response.name()).isEqualTo("Savings");
        assertThat(response.balance()).isEqualTo(BigDecimal.ZERO);
        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    void create_whenUserNotFound_throwsResourceNotFoundException() {
        when(userService.findByEmail("unknown@test.com"))
                .thenThrow(new ResourceNotFoundException("User not found"));

        assertThatThrownBy(() -> walletService.create(new WalletRequest("X"), "unknown@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByUser_returnsUserWallets() {
        Wallet w1 = new Wallet("Savings", user);
        w1.setId(1L);
        Wallet w2 = new Wallet("Expenses", user);
        w2.setId(2L);

        when(userService.findByEmail("alice@test.com")).thenReturn(user);
        when(walletRepository.findByUserId(1L)).thenReturn(List.of(w1, w2));

        List<WalletResponse> result = walletService.findByUser("alice@test.com");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(WalletResponse::name).containsExactly("Savings", "Expenses");
    }

    @Test
    void findById_whenAuthorized_returnsWallet() {
        Wallet wallet = new Wallet("Savings", user);
        wallet.setId(5L);

        when(walletRepository.findById(5L)).thenReturn(Optional.of(wallet));

        WalletResponse response = walletService.findById(5L, "alice@test.com");

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.name()).isEqualTo("Savings");
    }

    @Test
    void findById_whenWalletNotFound_throwsResourceNotFoundException() {
        when(walletRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.findById(99L, "alice@test.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void findById_whenUnauthorizedUser_throwsResourceNotFoundException() {
        User other = new User("bob", "bob@test.com", "encoded");
        Wallet wallet = new Wallet("Savings", other);
        wallet.setId(5L);

        when(walletRepository.findById(5L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.findById(5L, "alice@test.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
