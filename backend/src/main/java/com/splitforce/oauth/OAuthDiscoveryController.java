package com.splitforce.oauth;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth discovery documents and the JWKS endpoint. All are public and unauthenticated so an MCP
 * client can bootstrap the flow before it has any credentials.
 *
 * <ul>
 *   <li>RFC 9728 — protected resource metadata (points at the authorization server).</li>
 *   <li>RFC 8414 — authorization server metadata (endpoints + supported features).</li>
 * </ul>
 * Both are also served with a path suffix, which some MCP clients probe per RFC 9728 §3.
 */
@RestController
public class OAuthDiscoveryController {

    private static final List<String> SCOPES = List.of("mcp");

    private final OAuthUrls urls;
    private final JwkStore jwkStore;

    public OAuthDiscoveryController(OAuthUrls urls, JwkStore jwkStore) {
        this.urls = urls;
        this.jwkStore = jwkStore;
    }

    @GetMapping({"/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/**"})
    public Map<String, Object> protectedResource() {
        return Map.of(
                "resource", urls.resource(),
                "authorization_servers", List.of(urls.issuer()),
                "bearer_methods_supported", List.of("header"),
                "scopes_supported", SCOPES);
    }

    @GetMapping({"/.well-known/oauth-authorization-server", "/.well-known/oauth-authorization-server/**"})
    public Map<String, Object> authorizationServer() {
        return Map.ofEntries(
                Map.entry("issuer", urls.issuer()),
                Map.entry("authorization_endpoint", urls.authorizationEndpoint()),
                Map.entry("token_endpoint", urls.tokenEndpoint()),
                Map.entry("registration_endpoint", urls.registrationEndpoint()),
                Map.entry("jwks_uri", urls.jwksUri()),
                Map.entry("response_types_supported", List.of("code")),
                Map.entry("grant_types_supported", List.of("authorization_code")),
                Map.entry("code_challenge_methods_supported", List.of("S256")),
                Map.entry("token_endpoint_auth_methods_supported", List.of("none")),
                Map.entry("scopes_supported", SCOPES));
    }

    @GetMapping("/oauth2/jwks")
    public Map<String, Object> jwks() {
        return jwkStore.publicJwkSet().toJSONObject();
    }
}
