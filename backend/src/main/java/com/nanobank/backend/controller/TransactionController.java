package com.nanobank.backend.controller;

import com.nanobank.backend.dto.TransactionRequest;
import com.nanobank.backend.dto.TransactionResponse;
import com.nanobank.backend.dto.TransferRequest;
import com.nanobank.backend.dto.MoveTransactionRequest;
import com.nanobank.backend.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.create(request, auth.getName()));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransferRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.transfer(request, auth.getName()));
    }

    @PostMapping("/move")
    public ResponseEntity<TransactionResponse> move(@Valid @RequestBody MoveTransactionRequest request,
            Authentication auth) {
        return ResponseEntity.ok(transactionService.move(request, auth.getName()));
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> findByWallet(
            @RequestParam Long walletId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Authentication auth) {
        return ResponseEntity.ok(transactionService.findByFilters(walletId, category, startDate, endDate, auth.getName()));
    }
}
