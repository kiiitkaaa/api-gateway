package com.deshko.apigateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import com.deshko.apigateway.dto.CreateUserRequest;
import com.deshko.apigateway.dto.CreatedUser;
import com.deshko.apigateway.exception.DownstreamServiceException;
import java.time.LocalDate;
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
import static com.deshko.apigateway.filter.JwtAuthenticationFilter.*;

class UserServiceClientTest {
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

    private static final CreateUserRequest NEW_USER = new CreateUserRequest(
            "Ivan", "Petrov",
            LocalDate.of(1999, 5, 20), "ivan@example.com", null);

    @Test
    void createUserSendsInternalHeadersAndReadsId() {
        var client = new UserServiceClient(stub(HttpStatus.CREATED, "{\"id\":7,\"name\":\"Ivan\"}"),
                "http://user-service:8080");

        StepVerifier.create(client.createUser(NEW_USER))
                .expectNext(new CreatedUser(7L))
                .verifyComplete();

        assertThat(sent.get().method()).isEqualTo(HttpMethod.POST);
        assertThat(sent.get().url().toString()).isEqualTo("http://user-service:8080/api/v1/users");
        assertThat(sent.get().headers().getFirst(USER_ROLE)).isEqualTo("ADMIN");
        assertThat(sent.get().headers().getFirst(USER_ID)).isEqualTo("0");
        assertThat(sent.get().headers().getFirst(USER_EMAIL)).isNotBlank();
    }

    @Test
    void createUserMapsErrorStatusToDownstreamException() {
        var client = new UserServiceClient(stub(HttpStatus.CONFLICT, "{\"message\":\"exists\"}"),
                "http://user-service:8080");

        StepVerifier.create(client.createUser(NEW_USER))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(DownstreamServiceException.class);
                    var ex = (DownstreamServiceException) e;
                    assertThat(ex.getStatus().value()).isEqualTo(409);
                    assertThat(ex.getService()).isEqualTo("user-service");
                    assertThat(ex.getBody()).contains("exists");
                })
                .verify();
    }

    @Test
    void deleteUserCallsDeleteEndpoint() {
        var client = new UserServiceClient(stub(HttpStatus.NO_CONTENT, null),
                "http://user-service:8080");

        StepVerifier.create(client.deleteUser(7L)).verifyComplete();

        assertThat(sent.get().method()).isEqualTo(HttpMethod.DELETE);
        assertThat(sent.get().url().toString()).isEqualTo("http://user-service:8080/api/v1/users/7");
        assertThat(sent.get().headers().getFirst(USER_ROLE)).isEqualTo("ADMIN");
    }
}