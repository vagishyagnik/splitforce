package com.splitforce.token;

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

import com.splitforce.user.User;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates {@code Authorization: Bearer sf_...} requests. The principal has the same shape as a
 * cookie session (an OAuth2User keyed by email), so controllers don't care which was used.
 * A bearer header that doesn't verify is a 401, never a fallback to the cookie.
 */
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    /** Registration id on token-authenticated principals; see {@link #isTokenAuth}. */
    public static final String REGISTRATION_ID = "api-token";
    private static final List<GrantedAuthority> AUTHORITIES = AuthorityUtils.createAuthorityList("API_TOKEN");
    private static final String BEARER = "Bearer ";

    private final ApiTokenService tokens;

    public ApiTokenAuthenticationFilter(ApiTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            chain.doFilter(request, response);
            return;
        }
        Optional<User> user = tokens.authenticate(header.substring(BEARER.length()).trim());
        if (user.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("email", user.get().getEmail());
        if (user.get().getName() != null) attributes.put("name", user.get().getName());
        DefaultOAuth2User principal = new DefaultOAuth2User(AUTHORITIES, attributes, "email");
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new OAuth2AuthenticationToken(principal, AUTHORITIES, REGISTRATION_ID));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    public static boolean isTokenAuth(Object authentication) {
        return authentication instanceof OAuth2AuthenticationToken t
                && REGISTRATION_ID.equals(t.getAuthorizedClientRegistrationId());
    }
}
