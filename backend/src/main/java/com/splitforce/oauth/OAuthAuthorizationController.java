package com.splitforce.oauth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.splitforce.auth.SignedCookies;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * OAuth 2.1 authorization endpoint (Authorization Code + PKCE). The resource owner is authenticated
 * by the existing Google login: if there is no SplitForce session the request is parked in a signed
 * cookie and the browser is sent through Google, then {@link OAuthLoginSuccessHandler} resumes it.
 * Once a user is present a short-lived single-use code is issued and the browser is redirected back
 * to the client. Consent is implicit — the user is approving by completing login for this client.
 */
@RestController
public class OAuthAuthorizationController {

    /** Signed cookie holding the authorize URL to resume after Google login. */
    public static final String RESUME_COOKIE = "SF_OAUTH_RESUME";
    private static final Duration RESUME_TTL = Duration.ofMinutes(10);
    private static final Duration CODE_TTL = Duration.ofSeconds(60);

    private final OAuthClientRepository clients;
    private final OAuthAuthCodeRepository codes;
    private final OAuthUrls urls;
    private final SignedCookies cookies;

    public OAuthAuthorizationController(OAuthClientRepository clients, OAuthAuthCodeRepository codes,
                                        OAuthUrls urls, SignedCookies cookies) {
        this.clients = clients;
        this.codes = codes;
        this.urls = urls;
        this.cookies = cookies;
    }

    @GetMapping("/oauth2/authorize")
    public void authorize(@RequestParam(value = "response_type", required = false) String responseType,
                          @RequestParam(value = "client_id", required = false) String clientId,
                          @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                          @RequestParam(value = "code_challenge", required = false) String codeChallenge,
                          @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
                          @RequestParam(value = "scope", required = false) String scope,
                          @RequestParam(value = "state", required = false) String state,
                          @AuthenticationPrincipal OAuth2User principal,
                          HttpServletRequest request,
                          HttpServletResponse response) throws java.io.IOException {

        // Unknown client or unregistered redirect_uri: we must NOT redirect (could be an attacker's URI).
        OAuthClient client = clients.findById(clientId).orElse(null);
        if (client == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "unknown client_id");
            return;
        }
        if (redirectUri == null || client.getRedirectUris() == null
                || !client.getRedirectUris().contains(redirectUri)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "redirect_uri is not registered for this client");
            return;
        }

        // From here, protocol errors are reported back to the client via redirect (RFC 6749 §4.1.2.1).
        if (!"code".equals(responseType)) {
            redirectError(response, redirectUri, state, "unsupported_response_type", "only response_type=code is supported");
            return;
        }
        if (codeChallenge == null || codeChallenge.isBlank() || !"S256".equals(codeChallengeMethod)) {
            redirectError(response, redirectUri, state, "invalid_request", "PKCE with code_challenge_method=S256 is required");
            return;
        }

        if (principal == null || principal.getAttribute("email") == null) {
            // Park the request and send the user through Google login; resumed on success.
            String resumeUrl = urls.base() + "/oauth2/authorize"
                    + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
            cookies.write(response, RESUME_COOKIE, resumeUrl.getBytes(StandardCharsets.UTF_8), RESUME_TTL);
            response.sendRedirect(urls.base() + "/oauth2/authorization/google");
            return;
        }

        String email = principal.getAttribute("email");
        String code = OAuthCodes.newCode();
        OAuthAuthCode row = new OAuthAuthCode();
        row.setCodeHash(OAuthCodes.sha256(code));
        row.setClientId(clientId);
        row.setRedirectUri(redirectUri);
        row.setUserEmail(email);
        row.setUserName(principal.getAttribute("name"));
        row.setUserPicture(principal.getAttribute("picture"));
        row.setCodeChallenge(codeChallenge);
        row.setScope(scope == null || scope.isBlank() ? "mcp" : scope);
        row.setExpiresAt(Instant.now().plus(CODE_TTL).getEpochSecond());
        row.setCreatedAt(Instant.now().toString());
        codes.save(row);

        // Done with the parked request if one was set.
        cookies.clear(response, RESUME_COOKIE);

        UriComponentsBuilder cb = UriComponentsBuilder.fromUriString(redirectUri).queryParam("code", code);
        if (state != null) cb.queryParam("state", state);
        response.sendRedirect(cb.build(true).toUriString());
    }

    private void redirectError(HttpServletResponse response, String redirectUri, String state,
                               String error, String description) throws java.io.IOException {
        UriComponentsBuilder cb = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", error)
                .queryParam("error_description", description);
        if (state != null) cb.queryParam("state", state);
        response.sendRedirect(cb.encode(StandardCharsets.UTF_8).build().toUriString());
    }
}
