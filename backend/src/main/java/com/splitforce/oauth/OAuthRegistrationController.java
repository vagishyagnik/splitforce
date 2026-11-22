package com.splitforce.oauth;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dynamic Client Registration (RFC 7591). MCP clients register themselves here before starting the
 * authorization flow. Registration is open (no initial access token), which is the norm for MCP; the
 * record it creates grants nothing on its own — every token still requires a real Google login and
 * an exact redirect-URI match. Only public (PKCE) clients are issued, so no secret is returned.
 */
@RestController
public class OAuthRegistrationController {

    private static final int MAX_REDIRECT_URIS = 10;
    private static final int MAX_URI_LEN = 2000;
    private static final int MAX_NAME_LEN = 200;

    private final OAuthClientRepository clients;

    public OAuthRegistrationController(OAuthClientRepository clients) {
        this.clients = clients;
    }

    @PostMapping(path = "/oauth2/register", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        List<String> redirectUris = redirectUris(body.get("redirect_uris"));
        if (redirectUris.isEmpty()) {
            return error("invalid_redirect_uri", "redirect_uris is required and must contain valid absolute URIs");
        }

        String clientName = asString(body.get("client_name"));
        if (clientName != null && clientName.length() > MAX_NAME_LEN) {
            clientName = clientName.substring(0, MAX_NAME_LEN);
        }

        OAuthClient client = new OAuthClient();
        client.setClientId("mcp_" + UUID.randomUUID().toString().replace("-", ""));
        client.setClientName(clientName);
        client.setRedirectUris(redirectUris);
        client.setScope("mcp");
        client.setCreatedAt(Instant.now().toString());
        clients.save(client);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("client_id", client.getClientId());
        out.put("client_id_issued_at", Instant.now().getEpochSecond());
        out.put("redirect_uris", redirectUris);
        out.put("token_endpoint_auth_method", "none");
        out.put("grant_types", List.of("authorization_code"));
        out.put("response_types", List.of("code"));
        if (clientName != null) out.put("client_name", clientName);
        out.put("scope", "mcp");
        return ResponseEntity.status(HttpStatus.CREATED).body(out);
    }

    private static List<String> redirectUris(Object raw) {
        List<String> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;
        for (Object o : list) {
            if (out.size() >= MAX_REDIRECT_URIS) break;
            String s = asString(o);
            if (s == null || s.length() > MAX_URI_LEN || !isValidRedirectUri(s)) continue;
            out.add(s);
        }
        return out;
    }

    /** Must be an absolute URI. Loopback http and custom app schemes are allowed (common for native MCP clients). */
    private static boolean isValidRedirectUri(String s) {
        try {
            URI uri = URI.create(s);
            if (!uri.isAbsolute() || uri.getScheme() == null) return false;
            if (uri.getFragment() != null) return false; // redirect URIs must not carry a fragment
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static ResponseEntity<Map<String, Object>> error(String code, String desc) {
        return ResponseEntity.badRequest().body(Map.of("error", code, "error_description", desc));
    }
}
