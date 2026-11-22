package com.splitforce.oauth;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Issues and verifies the JWT access tokens that authenticate MCP calls. Tokens are RS256-signed by
 * the {@link JwkStore} key; the resource server (an in-process filter) verifies them with the same
 * key's public half — no network round-trip, which suits stateless Lambda.
 */
@Service
public class OAuthJwtService {

    /** Access-token lifetime. Clients refresh by re-running the flow; MCP sessions are long-lived in practice. */
    public static final long ACCESS_TOKEN_TTL_SECONDS = 3600;

    private final JwkStore jwks;
    private final OAuthUrls urls;

    public OAuthJwtService(JwkStore jwks, OAuthUrls urls) {
        this.jwks = jwks;
        this.urls = urls;
    }

    public record AccessToken(String value, long expiresInSeconds) {}

    public AccessToken issue(String email, String name, String picture, String clientId, String scope) {
        try {
            RSAKey key = jwks.signingKey();
            Instant now = Instant.now();
            Instant exp = now.plusSeconds(ACCESS_TOKEN_TTL_SECONDS);
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(urls.issuer())
                    .subject(email)
                    .audience(urls.resource())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(exp))
                    .jwtID(UUID.randomUUID().toString())
                    .claim("email", email)
                    .claim("client_id", clientId);
            if (name != null) claims.claim("name", name);
            if (picture != null) claims.claim("picture", picture);
            if (scope != null && !scope.isBlank()) claims.claim("scope", scope);

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                    claims.build());
            jwt.sign(new RSASSASigner(key));
            return new AccessToken(jwt.serialize(), ACCESS_TOKEN_TTL_SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("could not sign access token", e);
        }
    }

    /** Verified claims of a token: signature, issuer, audience and expiry all checked. */
    public record VerifiedToken(String email, String name, String picture) {}

    public Optional<VerifiedToken> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            RSAKey key = jwks.signingKey();
            if (!jwt.verify(new RSASSAVerifier(key.toPublicJWK()))) return Optional.empty();

            JWTClaimsSet c = jwt.getJWTClaimsSet();
            Date exp = c.getExpirationTime();
            if (exp == null || exp.toInstant().isBefore(Instant.now())) return Optional.empty();
            if (!urls.issuer().equals(c.getIssuer())) return Optional.empty();
            if (c.getAudience() == null || !c.getAudience().contains(urls.resource())) return Optional.empty();

            String email = c.getStringClaim("email");
            if (email == null || email.isBlank()) return Optional.empty();
            return Optional.of(new VerifiedToken(email, c.getStringClaim("name"), c.getStringClaim("picture")));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
