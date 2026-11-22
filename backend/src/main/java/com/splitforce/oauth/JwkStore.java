package com.splitforce.oauth;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

/**
 * Provides the RSA keypair that signs MCP access tokens. The key lives in DynamoDB so it is stable
 * across Lambda instances and restarts; it is generated on first use and written with a conditional
 * put, so concurrent cold starts converge on a single key.
 *
 * <p>The key is loaded lazily (never during Spring startup) so it is not captured in a SnapStart
 * snapshot, per AWS guidance about not snapshotting secrets.
 */
@Component
public class JwkStore {

    private static final Logger log = LoggerFactory.getLogger(JwkStore.class);

    private final OAuthSigningKeyRepository repo;
    private volatile RSAKey cached;

    public JwkStore(OAuthSigningKeyRepository repo) {
        this.repo = repo;
    }

    /** The full RSA JWK (with private key) used to sign tokens. */
    public RSAKey signingKey() {
        RSAKey local = cached;
        if (local != null) return local;
        synchronized (this) {
            if (cached != null) return cached;
            cached = loadOrCreate();
            return cached;
        }
    }

    /** Public JWK set served at the jwks_uri, for clients/resource servers to verify signatures. */
    public JWKSet publicJwkSet() {
        return new JWKSet(signingKey().toPublicJWK());
    }

    private RSAKey loadOrCreate() {
        RSAKey existing = read();
        if (existing != null) return existing;

        try {
            RSAKey generated = new RSAKeyGenerator(2048)
                    .keyID(UUID.randomUUID().toString())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();
            OAuthSigningKey row = new OAuthSigningKey();
            row.setId(OAuthSigningKey.ACTIVE);
            row.setKid(generated.getKeyID());
            row.setJwkJson(generated.toJSONString());
            row.setCreatedAt(Instant.now().toString());
            if (repo.putIfAbsent(row)) {
                log.info("Bootstrapped OAuth signing key {}", generated.getKeyID());
                return generated;
            }
            // Lost the race — another instance wrote first; use theirs.
            RSAKey other = read();
            if (other != null) return other;
            throw new IllegalStateException("signing key missing after a losing conditional write");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("could not generate OAuth signing key", e);
        }
    }

    private RSAKey read() {
        return repo.findActive().map(row -> {
            try {
                return RSAKey.parse(row.getJwkJson());
            } catch (Exception e) {
                throw new IllegalStateException("stored OAuth signing key is unreadable", e);
            }
        }).orElse(null);
    }
}
