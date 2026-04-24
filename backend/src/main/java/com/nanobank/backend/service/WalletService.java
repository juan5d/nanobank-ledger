package com.nanobank.backend.service;

import com.nanobank.backend.dto.WalletRequest;
import com.nanobank.backend.dto.WalletResponse;
import com.nanobank.backend.entity.User;
import com.nanobank.backend.entity.Wallet;
import com.nanobank.backend.exception.ResourceNotFoundException;
import com.nanobank.backend.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserService userService;

    public WalletService(WalletRepository walletRepository, UserService userService) {
        this.walletRepository = walletRepository;
        this.userService = userService;
    }

    @Transactional
    public WalletResponse create(WalletRequest request, String userEmail) {
        User user = userService.findByEmail(userEmail);
        Wallet wallet = new Wallet(request.name(), user);
        return toResponse(walletRepository.save(wallet));
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> findByUser(String userEmail) {
        User user = userService.findByEmail(userEmail);
        return walletRepository.findByUserId(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WalletResponse findById(Long id, String userEmail) {
        Wallet wallet = getWalletOrThrow(id);
        if (!wallet.getUser().getEmail().equals(userEmail)) {
            throw new ResourceNotFoundException("Wallet not found with id: " + id);
        }
        return toResponse(wallet);
    }

    Wallet getWalletOrThrow(Long id) {
        return walletRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found with id: " + id));
    }

    private WalletResponse toResponse(Wallet w) {
        return new WalletResponse(w.getId(), w.getName(), w.getBalance());
    }
}
