package com.deshko.apigateway.service;

import com.deshko.apigateway.client.AuthServiceClient;
import com.deshko.apigateway.client.UserServiceClient;
import com.deshko.apigateway.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {
    private final UserServiceClient userClient;
    private final AuthServiceClient authClient;

    public Mono<RegisterResponse> register(RegisterRequest req) {
        CreateUserRequest userRequest = new CreateUserRequest(
                req.name(), req.surname(), req.birthDate(), req.email(), req.cards());

        return userClient.createUser(userRequest)
                .flatMap(user -> saveCredentialsOrRollback(user.id(), req)
                        .thenReturn(new RegisterResponse(user.id(), req.login(), req.email())));
    }

    private Mono<Void> saveCredentialsOrRollback(Long userId, RegisterRequest req) {
        SaveCredentialsRequest credentials =
                new SaveCredentialsRequest(userId, req.login(), req.email(), req.password());

        return authClient.saveCredentials(credentials)
                .onErrorResume(authError -> {
                    log.warn("Saving credentials failed for userId={}, rolling back user: {}",
                            userId, authError.toString());
                    return rollbackUser(userId).then(Mono.error(authError));
                });
    }

    private Mono<Void> rollbackUser(Long userId) {
        return userClient.deleteUser(userId)
                .doOnSuccess(v -> log.info("Rolled back user {}", userId))
                .onErrorResume(rollbackError -> {
                    log.error("ROLLBACK FAILED, orphan user in user-service: userId={}", userId, rollbackError);
                    return Mono.empty();
                });
    }
}
