package com.deshko.apigateway.dto;

public record RegisterResponse(
        Long userId,
        String login,
        String email
) {}
