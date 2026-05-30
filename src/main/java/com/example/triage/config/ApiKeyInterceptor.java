package com.example.triage.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class ApiKeyInterceptor implements HandlerInterceptor {
    private final TriageProperties properties;

    public ApiKeyInterceptor(TriageProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!request.getRequestURI().startsWith("/v1/triage")) {
            return true;
        }
        String expected = properties.apiKey();
        String actual = request.getHeader("x-api-key");
        if (expected == null || expected.isBlank() || actual == null || !apiKeysMatch(expected, actual)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid x-api-key");
            return false;
        }
        return true;
    }

    private boolean apiKeysMatch(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
