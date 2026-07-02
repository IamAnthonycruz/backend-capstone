package com.readshelf.auth;

import com.readshelf.config.JwtProperties;
import com.readshelf.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Signs and (later) verifies JWTs. The signing key is derived once from the
 * configured secret; HMAC-SHA is inferred from the key length by signWith(...).
 */
@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        // HMAC key from the shared secret. Must be >= 256 bits (32 chars) or jjwt throws.
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Mint a signed access token for this user.
     *
     * TODO(human): build and return the compact token string. The jjwt 0.13 API:
     *   Jwts.builder()
     *       .subject(...)              // the user id (String) -> goes in the standard "sub" claim
     *       .claim("role", ...)        // custom claim for authorization
     *       .issuedAt(Date)            // now
     *       .expiration(Date)          // now + jwtProperties.accessTokenTtl()
     *       .signWith(signingKey)
     *       .compact();
     * Notes:
     *   - jjwt wants java.util.Date, not Instant. Use Date.from(instant).
     *   - compute "now" once (Instant.now()) and reuse it for issuedAt and expiration.
     */
    public String generateAccessToken(User user) {
        var now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus( jwtProperties.accessTokenTtl())))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parse + validate in one shot. Returns the token's claims if the signature is
     * valid AND the token hasn't expired; otherwise jjwt throws (ExpiredJwtException,
     * io.jsonwebtoken.security.SignatureException, MalformedJwtException, ...), all
     * subclasses of JwtException. The filter will treat any throw as "not authenticated."
     *
     * TODO(human): build and return the Claims. The jjwt 0.13 parser API:
     *   Jwts.parser()
     *       .verifyWith(signingKey)     // same key used to sign
     *       .build()
     *       .parseSignedClaims(token)   // throws if signature/exp invalid
     *       .getPayload();              // -> Claims (the body)
     * Don't catch anything here — let it throw so the filter can decide what to do.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

    }
}