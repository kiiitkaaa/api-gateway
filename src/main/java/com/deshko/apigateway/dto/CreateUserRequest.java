package com.deshko.apigateway.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record CreateUserRequest(
        String name,
        String surname,
        LocalDate birthDate,
        String email,
        List<Map<String, Object>> cards
) {}
