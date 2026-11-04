package com.deshko.apigateway.dto;

public record SaveCredentialsRequest(
        Long userId,
        String login,
        String email,
        String password
) {}
