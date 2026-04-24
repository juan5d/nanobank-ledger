package com.nanobank.backend.dto;

public record AuthResponse(String token, Long userId, String username) {}
