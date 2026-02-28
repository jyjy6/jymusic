package jymusic.jym_member_auth_service.common.jwt;

import io.jsonwebtoken.Jwts;
import jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_member_auth_service.common.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Component
public class JwtProvider {

    private final PrivateKey privateKey;
    private final RedisService redisService;

    private static final long ACCESS_TOKEN_VALIDITY = 30 * 60 * 1000L; // 30 minutes
    private static final long REFRESH_TOKEN_VALIDITY = 7 * 24 * 60 * 60 * 1000L; // 7 days

    public JwtProvider(RedisService redisService,
                       @Value("${jwt.private-key-path}") Resource privateKeyResource) throws Exception {
        this.redisService = redisService;
        this.privateKey = loadPrivateKey(privateKeyResource);
    }

    private PrivateKey loadPrivateKey(Resource resource) throws Exception {
        byte[] keyBytes = resource.getInputStream().readAllBytes();
        String pem = new String(keyBytes, StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\s", "")
                .trim();
        byte[] decoded = Base64.getDecoder().decode(pem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    public String createAccessToken(Long userId, String username, String role, String nickname) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", role)
                .claim("nickname", nickname)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_VALIDITY))
                .signWith(privateKey)
                .compact();
    }

    public String createRefreshToken(String username) {
        String token = Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_VALIDITY))
                .signWith(privateKey)
                .compact();

        // Store in Redis
        redisService.setValue("RT:" + username, token, Duration.ofMillis(REFRESH_TOKEN_VALIDITY));
        return token;
    }

    // Refresh Token Rotation (RTR)
    public String rotateRefreshToken(String formerToken, String username) {
        String storedToken = redisService.getValue("RT:" + username);

        if (storedToken == null || !storedToken.equals(formerToken)) {
            // Potential malicious activity or token theft
            redisService.deleteValue("RT:" + username);
            throw new GlobalException("유효하지 않거나 만료된 리프레시 토큰입니다. 다시 로그인해주세요.", "ERR_INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED);
        }

        return createRefreshToken(username);
    }
}
