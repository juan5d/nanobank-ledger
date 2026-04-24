package com.nanobank.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record WalletRequest(@NotBlank String name) {}
