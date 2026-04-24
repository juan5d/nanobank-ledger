package com.nanobank.backend.controller;

import com.nanobank.backend.dto.WalletRequest;
import com.nanobank.backend.dto.WalletResponse;
import com.nanobank.backend.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> create(@Valid @RequestBody WalletRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(walletService.create(request, auth.getName()));
    }

    @GetMapping
    public ResponseEntity<List<WalletResponse>> findAll(Authentication auth) {
        return ResponseEntity.ok(walletService.findByUser(auth.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> findById(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(walletService.findById(id, auth.getName()));
    }
}
