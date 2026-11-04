package com.deshko.apigateway.exception;

import java.util.function.Function;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

@Getter
public class DownstreamServiceException extends RuntimeException {
    private final String service;
    private final HttpStatusCode status;
    private final String body;

    public DownstreamServiceException(String service, HttpStatusCode status, String body) {
        super(service + " responded with " + status);
        this.service = service;
        this.status = status;
        this.body = body;
    }

    public static Function<ClientResponse, Mono<? extends Throwable>> from(String service) {
        return response -> response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> new DownstreamServiceException(service, response.statusCode(), body));
    }
}
