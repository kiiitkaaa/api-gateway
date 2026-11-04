package com.deshko.apigateway.controller;

import com.deshko.apigateway.dto.RegisterRequest;
import com.deshko.apigateway.dto.RegisterResponse;
import com.deshko.apigateway.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class RegistrationController {
    private final RegistrationService registrationService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }
}
