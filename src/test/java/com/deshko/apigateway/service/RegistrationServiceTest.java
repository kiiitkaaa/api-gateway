package com.deshko.apigateway.service;

import com.deshko.apigateway.client.*;
import com.deshko.apigateway.dto.CreatedUser;
import com.deshko.apigateway.dto.RegisterRequest;
import com.deshko.apigateway.dto.RegisterResponse;
import com.deshko.apigateway.dto.SaveCredentialsRequest;
import com.deshko.apigateway.exception.DownstreamServiceException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {
    @Mock UserServiceClient userClient;
    @Mock AuthServiceClient authClient;
    @InjectMocks RegistrationService service;

    private final RegisterRequest request = new RegisterRequest(
            "ivan", "password123", "Ivan", "Petrov",
            LocalDate.of(1999, 5, 20), "ivan@example.com", null);
    private final DownstreamServiceException conflict =
            new DownstreamServiceException("auth-service", HttpStatus.CONFLICT, "{}");

    @Test
    void createsUserThenSavesCredentialsWithUserId() {
        when(userClient.createUser(any())).thenReturn(Mono.just(new CreatedUser(7L)));
        when(authClient.saveCredentials(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.register(request))
                .expectNext(
                        new RegisterResponse(7L, "ivan", "ivan@example.com"))
                .verifyComplete();

        verify(authClient).saveCredentials(
                new SaveCredentialsRequest(7L, "ivan", "ivan@example.com", "password123"));
        verify(userClient, never()).deleteUser(any());
    }

    @Test
    void rollsBackUserWhenAuthServiceFails() {
        when(userClient.createUser(any())).thenReturn(Mono.just(new CreatedUser(7L)));
        when(authClient.saveCredentials(any())).thenReturn(Mono.error(conflict));
        when(userClient.deleteUser(7L)).thenReturn(Mono.empty());

        StepVerifier.create(service.register(request))
                .expectErrorSatisfies(e -> assertThat(e).isSameAs(conflict))
                .verify();

        verify(userClient).deleteUser(7L);
    }

    @Test
    void originalErrorIsReturnedEvenIfRollbackFails() {
        when(userClient.createUser(any())).thenReturn(Mono.just(new CreatedUser(7L)));
        when(authClient.saveCredentials(any())).thenReturn(Mono.error(conflict));
        when(userClient.deleteUser(7L)).thenReturn(Mono.error(new RuntimeException("boom")));

        StepVerifier.create(service.register(request))
                .expectErrorSatisfies(e -> assertThat(e).isSameAs(conflict))
                .verify();
    }

    @Test
    void doesNotCallAuthServiceWhenUserCreationFails() {
        var badRequest = new DownstreamServiceException("user-service", HttpStatus.BAD_REQUEST, "{}");
        when(userClient.createUser(any())).thenReturn(Mono.error(badRequest));

        StepVerifier.create(service.register(request))
                .expectErrorSatisfies(e -> assertThat(e).isSameAs(badRequest))
                .verify();

        verifyNoInteractions(authClient);
        verify(userClient, never()).deleteUser(any());
    }
}