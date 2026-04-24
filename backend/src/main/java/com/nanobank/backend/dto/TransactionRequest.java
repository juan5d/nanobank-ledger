package com.nanobank.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TransactionRequest(
        String description,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank String type,
        String category,
        @NotNull Long walletId) {
}
