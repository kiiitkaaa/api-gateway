package com.deshko.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "jwt.secret=test-secret-test-secret-test-secret-123")
class ApiGatewayApplicationTests {
    @Test
    void contextLoads() {}
}
