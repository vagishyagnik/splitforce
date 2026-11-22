package com.splitforce.mcp;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.splitforce.oauth.OAuthUrls;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 401 responder for the MCP endpoint. Emits the RFC 9728 {@code WWW-Authenticate: Bearer
 * resource_metadata=...} challenge so an MCP client can discover the authorization server and begin
 * the OAuth flow. A small JSON-RPC-ish error body is included for human debugging.
 */
public class McpAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final OAuthUrls urls;

    public McpAuthenticationEntryPoint(OAuthUrls urls) {
        this.urls = urls;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String metadata = urls.base() + "/.well-known/oauth-protected-resource";
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                "Bearer resource_metadata=\"" + metadata + "\"");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"error\":\"unauthorized\",\"error_description\":\"authentication required; see WWW-Authenticate\"}");
    }
}
