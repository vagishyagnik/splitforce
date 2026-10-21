package com.splitforce.config;

import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.splitforce.auth.CookieAuthorizationRequestRepository;
import com.splitforce.auth.CookieSecurityContextRepository;
import com.splitforce.token.ApiTokenAuthenticationFilter;
import com.splitforce.token.ApiTokenService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            CookieSecurityContextRepository sessionCookies,
                                            CookieAuthorizationRequestRepository authRequestCookies,
                                            ApiTokenService apiTokens,
                                            @Value("${app.auth.cookie-secure:false}") boolean secure,
                                            @Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId,
                                            @Value("${spring.security.oauth2.client.registration.google.client-secret}") String clientSecret)
            throws Exception {
        if (secure && ("changeme".equals(clientId) || "changeme".equals(clientSecret))) {
            throw new IllegalStateException("Google OAuth credentials are not set; set GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET");
        }
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            // No HttpSession: login state lives in signed cookies so any Lambda instance can serve a request.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(ctx -> ctx.securityContextRepository(sessionCookies))
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/api/health", "/error", "/pictures/**").permitAll()
                .requestMatchers("/api/**", "/receipts/**").authenticated()
                .anyRequest().permitAll()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            )
            .oauth2Login(oauth -> oauth
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestRepository(authRequestCookies))
                .userInfoEndpoint(userInfo -> userInfo.oidcUserService(verifiedEmailOnly()))
                .defaultSuccessUrl(frontendUrl + "/app/activities", true)
                .failureUrl(frontendUrl + "/?login=failed")
            )
            .logout(logout -> logout
                // CSRF protection is off, so restrict logout to POST (a GET could be triggered by any <img>).
                .logoutRequestMatcher(new AntPathRequestMatcher("/api/logout", "POST"))
                .logoutSuccessHandler((request, response, authentication) ->
                    response.setStatus(HttpStatus.NO_CONTENT.value()))
            )
            // Bearer tokens (MCP and other API clients) override whatever the cookie loaded.
            .addFilterBefore(new ApiTokenAuthenticationFilter(apiTokens), AnonymousAuthenticationFilter.class)
            // After authorization, so logout and the OAuth callback (which return earlier) aren't affected.
            .addFilterAfter(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                FilterChain chain) throws ServletException, IOException {
                    sessionCookies.refresh(request, response);
                    chain.doFilter(request, response);
                }
            }, AuthorizationFilter.class);
        return http.build();
    }

    /** Users are matched to placeholder members by email, so only accept emails Google has verified. */
    private static OAuth2UserService<OidcUserRequest, OidcUser> verifiedEmailOnly() {
        OidcUserService delegate = new OidcUserService();
        return request -> {
            OidcUser user = delegate.loadUser(request);
            if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                throw new OAuth2AuthenticationException(new OAuth2Error("unverified_email"), "Google email is not verified");
            }
            return user;
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of(frontendUrl));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
