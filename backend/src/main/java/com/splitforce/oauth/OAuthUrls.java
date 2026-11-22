package com.splitforce.oauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Resolves the absolute URLs used across the OAuth surface. Behind CloudFront the request Host is
 * the Lambda Function URL, so {@code app.public-url} (the CloudFront origin) is preferred when set;
 * locally it falls back to the current request's base URL. Issuer, endpoints and the protected
 * resource identifier all derive from the same base so discovery, tokens and validation agree.
 */
@Component
public class OAuthUrls {

    private final String publicUrl;

    public OAuthUrls(@Value("${app.public-url:}") String publicUrl) {
        this.publicUrl = publicUrl == null ? "" : stripTrailingSlash(publicUrl.trim());
    }

    public String base() {
        if (!publicUrl.isEmpty()) return publicUrl;
        return stripTrailingSlash(ServletUriComponentsBuilder.fromCurrentContextPath().toUriString());
    }

    public String issuer() { return base(); }
    public String authorizationEndpoint() { return base() + "/oauth2/authorize"; }
    public String tokenEndpoint() { return base() + "/oauth2/token"; }
    public String registrationEndpoint() { return base() + "/oauth2/register"; }
    public String jwksUri() { return base() + "/oauth2/jwks"; }

    /** RFC 8707 / 9728 protected-resource identifier — the MCP endpoint. */
    public String resource() { return base() + "/api/mcp"; }

    private static String stripTrailingSlash(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '/') end--;
        return s.substring(0, end);
    }
}
