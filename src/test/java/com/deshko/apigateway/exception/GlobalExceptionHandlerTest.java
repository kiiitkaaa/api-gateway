package com.deshko.apigateway.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientRequestException;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void clientError_withBody_isPassedThroughAsIs() {
        String body = "{\"message\":\"login already exists\"}";

        ResponseEntity<String> response = handler.handleDownstream(
                new DownstreamServiceException("auth-service", HttpStatus.CONFLICT, body));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isEqualTo(body);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void clientError_withEmptyBody_getsGenericMessageWithSameStatus() {
        ResponseEntity<String> response = handler.handleDownstream(
                new DownstreamServiceException("user-service", HttpStatus.NOT_FOUND, ""));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).contains("user-service");
    }

    @Test
    void serverError_isMappedTo502() {
        ResponseEntity<String> response = handler.handleDownstream(
                new DownstreamServiceException("user-service", HttpStatus.INTERNAL_SERVER_ERROR, "boom"));

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody()).contains("\"status\":502").doesNotContain("boom");
    }

    @Test
    void connectionFailure_isMappedTo503() {
        ResponseEntity<String> response =
                handler.handleUnavailable(mock(WebClientRequestException.class));

        assertThat(response.getStatusCode().value()).isEqualTo(503);
    }

    @Test
    void timeout_isMappedTo504() {
        ResponseEntity<String> response = handler.handleTimeout(new TimeoutException());

        assertThat(response.getStatusCode().value()).isEqualTo(504);
    }
}
