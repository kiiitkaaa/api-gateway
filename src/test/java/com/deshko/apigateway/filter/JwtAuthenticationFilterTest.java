package com.deshko.apigateway.filter;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import org.springframework.web.server.ServerWebExchange;
import static org.assertj.core.api.Assertions.assertThat;
import static com.deshko.apigateway.filter.JwtAuthenticationFilter.*;

class JwtAuthenticationFilterTest {
    private static final SecretKey KEY = key("0123456789abcdef0123456789abcdef");
    private static final SecretKey OTHER_KEY = key("fedcba9876543210fedcba9876543210");

    private final ReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
            .withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(decoder);

    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    private final WebFilterChain chain = exchange -> {
        forwarded.set(exchange);
        return Mono.empty();
    };

    @Test
    void publicLoginPassesAndDropsForgedIdentityHeaders() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/auth/login")
                .header(USER_ID, "1")
                .header(USER_ROLE, "ADMIN").header(USER_EMAIL, "x@y.z"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst(USER_ID)).isNull();
        assertThat(headers.getFirst(USER_ROLE)).isNull();
        assertThat(headers.getFirst(USER_EMAIL)).isNull();
    }

    @Test
    void registerRefreshAndHealthArePublic() {
        for (var request : new MockServerHttpRequest.BaseBuilder<?>[] {
                MockServerHttpRequest.post("/api/v1/auth/register"),
                MockServerHttpRequest.post("/api/v1/auth/refresh"),
                MockServerHttpRequest.get("/actuator/health")}) {
            forwarded.set(null);
            filter.filter(MockServerWebExchange.from(request), chain).block();
            assertThat(forwarded.get()).isNotNull();
        }
    }

    @Test
    void validAccessTokenAddsIdentityHeaders() {
        var exchange = get("/api/v1/users/me",
                token(KEY, "access", Duration.ofMinutes(10), "USER", "a@b.c"));

        filter.filter(exchange, chain).block();

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst(USER_ID)).isEqualTo("42");
        assertThat(headers.getFirst(USER_ROLE)).isEqualTo("USER");
        assertThat(headers.getFirst(USER_EMAIL)).isEqualTo("a@b.c");
    }

    @Test
    void forgedRoleHeaderIsOverwrittenByTokenClaims() {
        String jwt = token(KEY, "access", Duration.ofMinutes(10), "USER", "a@b.c");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/users")
                .header("Authorization", "Bearer " + jwt)
                .header(USER_ROLE, "ADMIN"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst(USER_ROLE)).isEqualTo("USER");
    }

    @Test
    void missingTokenIsUnauthorized() {
        assertUnauthorized(MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/users/me")));
    }

    @Test
    void forgedHeadersWithoutTokenAreUnauthorized() {
        assertUnauthorized(MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/users/me")
                .header(USER_ID, "1")
                .header(USER_ROLE, "ADMIN").header(USER_EMAIL, "a@b.c")));
    }

    @Test
    void credentialsEndpointIsNotPublic() {
        assertUnauthorized(MockServerWebExchange.from(MockServerHttpRequest
                .post("/api/v1/auth/credentials")));
    }

    @Test
    void garbageTokenIsUnauthorized() {
        assertUnauthorized(get("/api/v1/users/me", "abc.def.ghi"));
    }

    @Test
    void tokenSignedWithAnotherKeyIsUnauthorized() {
        assertUnauthorized(get("/api/v1/users/me",
                token(OTHER_KEY, "access", Duration.ofMinutes(10), "USER", "a@b.c")));
    }

    @Test
    void expiredTokenIsUnauthorized() {
        assertUnauthorized(get("/api/v1/users/me", token(KEY, "access",
                Duration.ofMinutes(-5), "USER", "a@b.c")));
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        assertUnauthorized(get("/api/v1/users/me", token(KEY, "refresh",
                Duration.ofDays(1), "USER", null)));
    }

    @Test
    void tokenWithoutEmailIsUnauthorized() {
        assertUnauthorized(get("/api/v1/users/me", token(KEY, "access",
                Duration.ofMinutes(10), "USER", null)));
    }

    private void assertUnauthorized(MockServerWebExchange exchange) {
        filter.filter(exchange, chain).block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    private static MockServerWebExchange get(String path, String jwt) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .header("Authorization", "Bearer " + jwt));
    }

    private static SecretKey key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static String token(SecretKey key, String type, Duration ttl, String role, String email) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject("42")
                .issuedAt(now.minus(Duration.ofHours(1)))
                .expiresAt(now.plus(ttl))
                .claim("type", type);
        if (role != null) claims.claim("role", role);
        if (email != null) claims.claim("email", email);

        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }
}