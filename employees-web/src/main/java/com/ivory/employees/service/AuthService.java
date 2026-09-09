package com.ivory.employees.service;

import com.ivory.employees.config.AppConfig;
import com.ivory.employees.dao.UserDao;
import com.ivory.employees.util.ApiException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The LOGIN service: verifies credentials against APP_USERS and hands out a bearer token that the
 * other services require.
 *
 * <p>Passwords are stored as PBKDF2-HMAC-SHA256 hashes with a per-user random salt (JDK only, no
 * external crypto library). Sessions live in memory and expire after
 * {@code employees.session.ttl.minutes}.
 */
public final class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;

    /** An authenticated session. */
    public record Session(String token, String username, Instant issuedAt, Instant expiresAt) {

        public boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    private final UserDao userDao;
    private final Duration sessionTtl;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    public AuthService(UserDao userDao, AppConfig config) {
        this.userDao = userDao;
        this.sessionTtl = config.sessionTtl();
        seedUsers(config.seedUsers());
    }

    /** Creates the configured users on first start; existing users are left untouched. */
    private void seedUsers(Map<String, String> users) {
        users.forEach((username, password) -> {
            String salt = HexFormat.of().formatHex(randomBytes());
            String hash = hash(password, salt);
            if (userDao.insertIfAbsent(new UserDao.Credentials(username, salt, hash))) {
                LOG.info(() -> "Created application user '" + username + "'");
            }
        });
    }

    /**
     * Verifies the credentials and opens a session.
     *
     * @throws ApiException 401 when the user is unknown or the password does not match
     */
    public Session login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw ApiException.badRequest("'username' and 'password' are required");
        }
        Optional<UserDao.Credentials> stored = userDao.find(username);
        if (stored.isEmpty() || !matches(password, stored.get())) {
            LOG.warning(() -> "Failed login attempt for user '" + username + "'");
            throw ApiException.unauthorized("Invalid username or password");
        }
        Instant now = Instant.now();
        Session session = new Session(UUID.randomUUID().toString().replace("-", ""),
                username, now, now.plus(sessionTtl));
        sessions.put(session.token(), session);
        LOG.info(() -> "User '" + username + "' logged in, session valid until " + session.expiresAt());
        return session;
    }

    /** Returns the live session for a token, or empty when unknown or expired. */
    public Optional<Session> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.isExpired(Instant.now())) {
            sessions.remove(token, session);
            LOG.fine(() -> "Session of user '" + session.username() + "' has expired");
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void logout(String token) {
        Session removed = token == null ? null : sessions.remove(token);
        if (removed != null) {
            LOG.info(() -> "User '" + removed.username() + "' logged out");
        }
    }

    /** Drops expired sessions; returns how many were removed. */
    public int purgeExpiredSessions() {
        Instant now = Instant.now();
        int before = sessions.size();
        sessions.values().removeIf(session -> session.isExpired(now));
        return before - sessions.size();
    }

    public int activeSessions() {
        return sessions.size();
    }

    public Duration sessionTtl() {
        return sessionTtl;
    }

    private boolean matches(String password, UserDao.Credentials credentials) {
        String candidate = hash(password, credentials.salt());
        return MessageDigest.isEqual(
                candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                credentials.passwordHash().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private byte[] randomBytes() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        random.nextBytes(salt);
        return salt;
    }

    static String hash(String password, String saltHex) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(),
                    HexFormat.of().parseHex(saltHex), ITERATIONS, KEY_LENGTH_BITS);
            byte[] key = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(key);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Unable to hash the password", e);
        }
    }
}
