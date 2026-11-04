package com.deshko.apigateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import com.deshko.apigateway.dto.SaveCredentialsRequest;
import com.deshko.apigateway.exception.DownstreamServiceException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthServiceClientTest {
    private final AtomicReference<ClientRequest> sent = new AtomicReference<>();

    private WebClient.Builder stub(HttpStatus status, String body) {
        return WebClient.builder().exchangeFunction(request -> {
            sent.set(request);
            ClientResponse.Builder response = ClientResponse.create(status);
            if (body != null) {
                response.header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(body);
            }
            return Mono.just(response.build());
        });
    }

    private static final SaveCredentialsRequest CREDENTIALS =
            new SaveCredentialsRequest(7L, "ivan", "ivan@example.com", "password123");

    @Test
    void saveCredentialsPostsToAuthService() {
        var client = new AuthServiceClient(stub(HttpStatus.CREATED, null), "http://auth-service:8081");

        StepVerifier.create(client.saveCredentials(CREDENTIALS)).verifyComplete();

        assertThat(sent.get().method()).isEqualTo(HttpMethod.POST);
        assertThat(sent.get()
                .url()
                .toString())
                .isEqualTo("http://auth-service:8081/api/v1/auth/credentials");
    }

    @Test
    void saveCredentialsMapsErrorStatus() {
        var client = new AuthServiceClient(stub(HttpStatus.INTERNAL_SERVER_ERROR, null),
                "http://auth-service:8081");

        StepVerifier.create(client.saveCredentials(CREDENTIALS))
                .expectErrorSatisfies(e -> assertThat(((DownstreamServiceException) e).getStatus().value())
                        .isEqualTo(500))
                .verify();
    }
}
