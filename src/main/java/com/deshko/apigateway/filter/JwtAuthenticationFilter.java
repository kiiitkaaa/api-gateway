package com.deshko.apigateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {
    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    public static final String USER_EMAIL = "X-User-Email";
    private static final String BEARER = "Bearer ";
    private static final Set<String> PUBLIC_ENDPOINTS = Set.of(
            "POST /api/v1/auth/login",
            "POST /api/v1/auth/register",
            "POST /api/v1/auth/refresh");

    private final ReactiveJwtDecoder jwtDecoder;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String endpoint = request.getMethod().name() + " " + path;

        if (isPublic(endpoint, path)) {
            return chain.filter(withoutIdentityHeaders(exchange));
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return unauthorized(exchange, "Missing bearer token");
        }

        return jwtDecoder.decode(authorization.substring(BEARER.length()).trim())
                .flatMap(jwt -> authenticate(exchange, chain, jwt))
                .onErrorResume(JwtException.class,
                        e -> unauthorized(exchange, "Invalid or expired token"));
    }

    private Mono<Void> authenticate(ServerWebExchange exchange, WebFilterChain chain, Jwt jwt) {
        String userId = jwt.getSubject();
        String role = jwt.getClaimAsString("role");
        String email = jwt.getClaimAsString("email");

        if (!"access".equals(jwt.getClaimAsString("type"))
                || !StringUtils.hasText(userId)
                || !StringUtils.hasText(role)
                || !StringUtils.hasText(email)) {
            return unauthorized(exchange, "Invalid token");
        }

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(USER_ID);
                    h.remove(USER_ROLE);
                    h.remove(USER_EMAIL);
                    h.set(USER_ID, userId);
                    h.set(USER_ROLE, role);
                    h.set(USER_EMAIL, email);
                })
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean isPublic(String endpoint, String path) {
        return PUBLIC_ENDPOINTS.contains(endpoint)
                || path.equals("/actuator/health")
                || path.startsWith("/actuator/health/");
    }

    private ServerWebExchange withoutIdentityHeaders(ServerWebExchange exchange) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(USER_ID);
                    h.remove(USER_ROLE);
                    h.remove(USER_EMAIL);
                })
                .build();
        return exchange.mutate().request(mutated).build();
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\""
                + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
