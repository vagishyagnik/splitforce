package com.splitforce.mcp;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.filter.OncePerRequestFilter;

import com.splitforce.oauth.OAuthJwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Validates OAuth JWT access tokens (the MCP resource-server role). A {@code Bearer} header that
 * looks like a JWT is verified against the local signing key; on success the principal is an
 * {@link org.springframework.security.oauth2.core.user.OAuth2User} keyed by email — the same shape
 * cookie and PAT auth produce — so downstream code is unaffected. A JWT-shaped token that fails
 * verification is a 401. Non-JWT bearer tokens (personal access tokens) are left for the next filter.
 */
public class McpJwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String REGISTRATION_ID = "mcp-oauth";
    private static final List<GrantedAuthority> AUTHORITIES = AuthorityUtils.createAuthorityList("OAUTH2_USER", "MCP");
    private static final String BEARER = "Bearer ";

    private final OAuthJwtService jwt;

    public McpJwtAuthenticationFilter(OAuthJwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER.length()).trim();
        if (!looksLikeJwt(token)) {
            // Likely a personal access token; let the PAT filter handle it.
            chain.doFilter(request, response);
            return;
        }

        Optional<OAuthJwtService.VerifiedToken> verified = jwt.verify(token);
        if (verified.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        OAuthJwtService.VerifiedToken v = verified.get();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("email", v.email());
        if (v.name() != null) attributes.put("name", v.name());
        if (v.picture() != null) attributes.put("picture", v.picture());

        DefaultOAuth2User principal = new DefaultOAuth2User(AUTHORITIES, attributes, "email");
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new OAuth2AuthenticationToken(principal, AUTHORITIES, REGISTRATION_ID));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    /** A compact JWS has three base64url segments separated by dots; PATs (sf_...) never do. */
    private static boolean looksLikeJwt(String token) {
        int first = token.indexOf('.');
        if (first <= 0) return false;
        int second = token.indexOf('.', first + 1);
        return second > first && token.indexOf('.', second + 1) < 0 && second < token.length() - 1;
    }
}
