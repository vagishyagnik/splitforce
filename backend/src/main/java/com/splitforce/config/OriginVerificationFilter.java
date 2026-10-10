package com.splitforce.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The Lambda Function URL is public, so CloudFront adds a secret header to every origin request
 * and anything arriving without it (i.e. straight at the Function URL) is rejected.
 * Disabled when {@code app.origin-secret} is empty, as in local dev.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OriginVerificationFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Origin-Verify";

    private final byte[] secret;

    public OriginVerificationFilter(@Value("${app.origin-secret:}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (secret.length > 0) {
            String given = request.getHeader(HEADER);
            if (given == null || !MessageDigest.isEqual(secret, given.getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
