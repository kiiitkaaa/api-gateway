package com.deshko.apigateway.exception;

import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<String> handleDownstream(DownstreamServiceException ex) {
        if (ex.getStatus().is4xxClientError() && !ex.getBody().isBlank()) {
            return ResponseEntity.status(ex.getStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getBody());
        }
        if (ex.getStatus().is4xxClientError()) {
            return error(HttpStatus.valueOf(ex
                    .getStatus()
                    .value()), "Request rejected by " + ex.getService());
        }
        return error(HttpStatus.BAD_GATEWAY, ex.getService() + " failed");
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<String> handleUnavailable(WebClientRequestException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Downstream service is unavailable");
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<String> handleTimeout(TimeoutException ex) {
        return error(HttpStatus.GATEWAY_TIMEOUT, "Downstream service timed out");
    }

    private ResponseEntity<String> error(HttpStatus status, String message) {
        String body = "{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase()
                + "\",\"message\":\"" + message + "\"}";
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
