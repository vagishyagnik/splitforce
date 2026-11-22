package com.splitforce.oauth;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth 2.1 token endpoint. Exchanges a single-use authorization code (proved with PKCE) for a
 * signed JWT access token. Public clients only — the PKCE code_verifier is the proof, so no client
 * secret is required or accepted.
 */
@RestController
public class OAuthTokenController {

    private final OAuthAuthCodeRepository codes;
    private final OAuthJwtService jwt;

    public OAuthTokenController(OAuthAuthCodeRepository codes, OAuthJwtService jwt) {
        this.codes = codes;
        this.jwt = jwt;
    }

    @PostMapping(path = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "code_verifier", required = false) String codeVerifier) {

        if (!"authorization_code".equals(grantType)) {
            return error("unsupported_grant_type", "only grant_type=authorization_code is supported");
        }
        if (code == null || code.isBlank()) {
            return error("invalid_request", "code is required");
        }

        // Redemption deletes the code atomically, enforcing single use.
        OAuthAuthCode row = codes.redeem(OAuthCodes.sha256(code)).orElse(null);
        if (row == null) {
            return error("invalid_grant", "authorization code is invalid, expired or already used");
        }
        if (row.getExpiresAt() == null || row.getExpiresAt() < Instant.now().getEpochSecond()) {
            return error("invalid_grant", "authorization code has expired");
        }
        if (clientId == null || !clientId.equals(row.getClientId())) {
            return error("invalid_grant", "client_id does not match the authorization code");
        }
        if (redirectUri == null || !redirectUri.equals(row.getRedirectUri())) {
            return error("invalid_grant", "redirect_uri does not match the authorization request");
        }
        if (!OAuthCodes.verifyPkce(codeVerifier, row.getCodeChallenge())) {
            return error("invalid_grant", "PKCE verification failed");
        }

        OAuthJwtService.AccessToken token = jwt.issue(
                row.getUserEmail(), row.getUserName(), row.getUserPicture(), clientId, row.getScope());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", token.value());
        body.put("token_type", "Bearer");
        body.put("expires_in", token.expiresInSeconds());
        if (row.getScope() != null) body.put("scope", row.getScope());

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(body);
    }

    private static ResponseEntity<Map<String, Object>> error(String code, String desc) {
        return ResponseEntity.badRequest()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("error", code, "error_description", desc));
    }
}
