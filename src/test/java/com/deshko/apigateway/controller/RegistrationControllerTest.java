package com.deshko.apigateway.controller;

import com.deshko.apigateway.dto.RegisterResponse;
import com.deshko.apigateway.exception.DownstreamServiceException;
import com.deshko.apigateway.exception.GlobalExceptionHandler;
import java.util.concurrent.TimeoutException;
import com.deshko.apigateway.service.RegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RegistrationControllerTest {
    private static final String VALID_BODY = """
            {"login":"ivan","password":"password123","name":"Ivan","surname":"Petrov",
             "birthDate":"1999-05-20","email":"ivan@example.com"}""";
    private final RegistrationService service = mock(RegistrationService.class);
    private final WebTestClient client = WebTestClient
            .bindToController(new RegistrationController(service))
            .controllerAdvice(new GlobalExceptionHandler())
            .build();

    private WebTestClient.ResponseSpec post(String body) {
        return client.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    @Test
    void returns201WithCreatedUser() {
        when(service.register(any()))
                .thenReturn(Mono.just(new RegisterResponse(7L, "ivan", "ivan@example.com")));

        post(VALID_BODY).expectStatus().isCreated()
                .expectBody().jsonPath("$.userId").isEqualTo(7)
                .jsonPath("$.login").isEqualTo("ivan");
    }

    @Test
    void rejectsShortPasswordBeforeCallingServices() {
        post(VALID_BODY.replace("password123", "123"))
                .expectStatus().isBadRequest();

        verifyNoInteractions(service);
    }

    @Test
    void passesThrough4xxFromDownstream() {
        when(service.register(any())).thenReturn(Mono.error(new DownstreamServiceException(
                "auth-service", HttpStatus.CONFLICT, "{\"message\":\"login taken\"}")));

        post(VALID_BODY).expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody().jsonPath("$.message").isEqualTo("login taken");
    }

    @Test
    void mapsDownstream5xxTo502() {
        when(service.register(any())).thenReturn(Mono.error(new DownstreamServiceException(
                "user-service", HttpStatus.INTERNAL_SERVER_ERROR, "")));

        post(VALID_BODY).expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void mapsTimeoutTo504() {
        when(service.register(any())).thenReturn(Mono.error(new TimeoutException()));

        post(VALID_BODY).expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }
}