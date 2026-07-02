package com.readshelf.auth;

import com.readshelf.config.JwtProperties;
import com.readshelf.user.User;
import com.readshelf.user.UserMapper;
import com.readshelf.user.UserRepository;
import com.readshelf.user.UserResponseDTO;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Auth flows that aren't plain CRUD: register, login, and refresh-token rotation.
 * Lives in its own package/service rather than UserService because the concern is
 * authentication, not user management.
 */
@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       UserMapper userMapper,
                       JwtService jwtService,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    public UserResponseDTO register(RegisterRequest request) {
        User user = new User();
        user.setEmail(request.email());
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole("BORROWER");
        try{
            userRepository.saveAndFlush(user);
        } catch( DataIntegrityViolationException d){
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User already exists");
        }
        return userMapper.toResponseDTO(user);
    }

    public AuthResponse login(LoginRequest request) {
        var user = userRepository.findByUsername(request.username());
        if(user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username or password is incorrect");
        }
        if(!passwordEncoder.matches(request.password(), user.get().getPassword())){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Username or password is incorrect");
        }
        // A fresh login starts a new token family (lineage).
        String refreshToken = issueRefreshToken(user.get(), UUID.randomUUID());
        return new AuthResponse(jwtService.generateAccessToken(user.get()), refreshToken);
    }

    /**
     * Exchange a valid refresh token for a fresh access + refresh pair (rotation).
     */
    // dontRollbackOn: the reuse-detection branch revokes the family and THEN throws a 401.
    // Without this, the rollback-on-RuntimeException default would undo the revoke, making
    // reuse detection toothless. No other throw path writes anything, so this is safe.
    @Transactional(dontRollbackOn = ResponseStatusException.class)
    public AuthResponse refresh(RefreshRequest request) {
        // TODO(human): validate + rotate. The plan we agreed (you decide exact order/guards):
        var hashedToken = hashToken(request.refreshToken());
        var dbToken = refreshTokenRepository.findByTokenHash(hashedToken);
        if (dbToken.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid");
        }
        if(dbToken.get().isRevoked()){
            refreshTokenRepository.revokeFamily(dbToken.get().getFamilyId());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid");
        }
        if (dbToken.get().getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
        // Rotate: spend (revoke) the presented token, then issue a new one in the SAME
        // family so the lineage — and reuse detection — continues.
        RefreshToken current = dbToken.get();
        current.setRevoked(true);
        refreshTokenRepository.saveAndFlush(current);

        User user = current.getUser();
        String newRefreshToken = issueRefreshToken(user, current.getFamilyId());
        return new AuthResponse(jwtService.generateAccessToken(user), newRefreshToken);
    }

    /**
     * Mint + persist a refresh token in the given family and return its RAW value (the
     * only time it's exposed; only the hash is stored). Shared by login (new family) and
     * refresh (existing family).
     */
    private String issueRefreshToken(User user, UUID familyId) {
        String raw = generateRefreshTokenValue();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setFamilyId(familyId);
        refreshToken.setTokenHash(hashToken(raw));
        refreshToken.setRevoked(false);
        refreshToken.setExpiresAt(Instant.now().plus(jwtProperties.refreshTokenTtl()));
        refreshTokenRepository.saveAndFlush(refreshToken);
        return raw;
    }

    // ---- crypto helpers (boilerplate; the interesting logic is the flows above) ----

    /**
     * A high-entropy (256-bit) URL-safe random token. This RAW value is handed to the
     * client exactly once and never stored — only its hash is persisted.
     */
    private String generateRefreshTokenValue() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Deterministic hash so the same token always maps to the same stored value (that's
     * what makes findByTokenHash possible). SHA-256 is appropriate here: unlike a
     * password, the input is already high-entropy random, so a slow salted hash like
     * BCrypt would buy nothing.
     */
    private String hashToken(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
