package com.nanobank.backend.dto;

import jakarta.validation.constraints.NotNull;

public record MoveTransactionRequest(
        @NotNull Long transactionId,
        @NotNull Long targetWalletId) {
}
