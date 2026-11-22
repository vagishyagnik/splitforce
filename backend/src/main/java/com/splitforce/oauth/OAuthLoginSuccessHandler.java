package com.splitforce.oauth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.splitforce.auth.SignedCookies;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs after a successful Google login. If an OAuth authorize request was parked (because the user
 * wasn't logged in when an MCP client sent them to {@code /oauth2/authorize}), resume it; otherwise
 * land on the web app. The parked URL must sit under our own authorize endpoint, so this can't be
 * turned into an open redirect.
 */
@Component
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final SignedCookies cookies;
    private final OAuthUrls urls;
    private final String appLanding;

    public OAuthLoginSuccessHandler(SignedCookies cookies, OAuthUrls urls,
                                    @Value("${app.frontend-url}") String frontendUrl) {
        this.cookies = cookies;
        this.urls = urls;
        this.appLanding = frontendUrl + "/app/activities";
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String resume = cookies.read(request, OAuthAuthorizationController.RESUME_COOKIE)
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .orElse(null);
        cookies.clear(response, OAuthAuthorizationController.RESUME_COOKIE);

        if (resume != null && resume.startsWith(urls.base() + "/oauth2/authorize")) {
            response.sendRedirect(resume);
            return;
        }
        response.sendRedirect(appLanding);
    }
}
