package com.deshko.apigateway.client;

import com.deshko.apigateway.dto.CreateUserRequest;
import com.deshko.apigateway.dto.CreatedUser;
import com.deshko.apigateway.exception.DownstreamServiceException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class UserServiceClient {
    private static final String SERVICE = "user-service";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;

    public UserServiceClient(WebClient.Builder builder, @Value("${downstream.user.url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Mono<CreatedUser> createUser(CreateUserRequest request) {
        return webClient.post()
                .uri("/api/v1/users")
                .headers(InternalHeaders::apply)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, DownstreamServiceException.from(SERVICE))
                .bodyToMono(CreatedUser.class)
                .timeout(TIMEOUT);
    }

    public Mono<Void> deleteUser(Long id) {
        return webClient.delete()
                .uri("/api/v1/users/{id}", id)
                .headers(InternalHeaders::apply)
                .retrieve()
                .onStatus(HttpStatusCode::isError, DownstreamServiceException.from(SERVICE))
                .bodyToMono(Void.class)
                .timeout(TIMEOUT);
    }
}
