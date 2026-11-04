package com.deshko.apigateway.client;

import com.deshko.apigateway.dto.SaveCredentialsRequest;
import com.deshko.apigateway.exception.DownstreamServiceException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class AuthServiceClient {
    private static final String SERVICE = "auth-service";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;

    public AuthServiceClient(WebClient.Builder builder, @Value("${downstream.auth.url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Mono<Void> saveCredentials(SaveCredentialsRequest request) {
        return webClient.post()
                .uri("/api/v1/auth/credentials")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, DownstreamServiceException.from(SERVICE))
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .then();
    }
}
