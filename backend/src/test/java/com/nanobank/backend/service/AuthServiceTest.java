package com.nanobank.backend.service;

import com.nanobank.backend.dto.AuthResponse;
import com.nanobank.backend.dto.LoginRequest;
import com.nanobank.backend.dto.RegisterRequest;
import com.nanobank.backend.entity.User;
import com.nanobank.backend.repository.UserRepository;
import com.nanobank.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authManager;

    @InjectMocks
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("alice", "alice@test.com", "encoded");
        user.setId(1L);
    }

    @Test
    void register_newUser_returnsAuthResponse() {
        RegisterRequest req = new RegisterRequest("alice", "alice@test.com", "password123");

        when(userRepository.existsByEmail("alice@test.com")).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(userRepository.save(any())).thenReturn(user);
        when(jwtService.generateToken("alice@test.com")).thenReturn("jwt-token");

        AuthResponse response = authService.register(req);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.userId()).isEqualTo(1L);
    }

    @Test
    void register_emailAlreadyExists_throwsIllegalArgumentException() {
        when(userRepository.existsByEmail("alice@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice", "alice@test.com", "pass123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email");
    }

    @Test
    void register_usernameAlreadyExists_throwsIllegalArgumentException() {
        when(userRepository.existsByEmail("alice@test.com")).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice", "alice@test.com", "pass123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username");
    }

    @Test
    void login_validCredentials_returnsAuthResponse() {
        LoginRequest req = new LoginRequest("alice@test.com", "password123");

        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(userRepository.findByEmail("alice@test.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("alice@test.com")).thenReturn("jwt-token");

        AuthResponse response = authService.login(req);

        assertThat(response.token()).isEqualTo("jwt-token");
        verify(authManager).authenticate(any());
    }

    @Test
    void login_invalidCredentials_throwsBadCredentialsException() {
        LoginRequest req = new LoginRequest("alice@test.com", "wrong-password");

        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials");
    }
}
