package com.nanobank.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        String description,
        BigDecimal amount,
        String type,
        String category,
        LocalDateTime date,
        Long walletId) {
}
