package com.deshko.apigateway.client;

import com.deshko.apigateway.filter.JwtAuthenticationFilter;
import org.springframework.http.HttpHeaders;

public final class InternalHeaders {
    private InternalHeaders() {}

    static void apply(HttpHeaders headers) {
        headers.set(JwtAuthenticationFilter.USER_ID, "0");
        headers.set(JwtAuthenticationFilter.USER_ROLE, "ADMIN");
        headers.set(JwtAuthenticationFilter.USER_EMAIL, "gateway@internal");
    }
}
