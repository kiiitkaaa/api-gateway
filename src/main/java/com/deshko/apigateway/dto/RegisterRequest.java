package com.deshko.apigateway.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        String login,

        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Size(max = 100)
        String surname,

        @NotNull
        @Past LocalDate
        birthDate,

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        List<Map<String, Object>> cards
) {}
