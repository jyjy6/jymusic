package jy.Job_Flow_Agent.Auth.JWT;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import jy.Job_Flow_Agent.GlobalErrorHandler.GlobalException;
import jy.Job_Flow_Agent.Member.DTO.MemberDto;
import jy.Job_Flow_Agent.Member.Entity.Member;
import jy.Job_Flow_Agent.Member.Service.CustomUserDetails;
import jy.Job_Flow_Agent.Member.Service.CustomUserDetailsService;
import jy.Job_Flow_Agent.Redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JWTUtil {

    private final CustomUserDetailsService customUserDetailsService;
    private final RedisService redisService;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public JWTUtil(CustomUserDetailsService customUserDetailsService,
                   RedisService redisService,
                   @Value("${jwt.private-key}") Resource privateKeyResource,
                   @Value("${jwt.public-key}") Resource publicKeyResource) throws Exception {
        this.customUserDetailsService = customUserDetailsService;
        this.redisService = redisService;

        // Private Key 로드
        byte[] privateKeyBytes = privateKeyResource.getInputStream().readAllBytes();
        String privateKeyPEM = new String(privateKeyBytes, StandardCharsets.UTF_8)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "")
                .trim();
        byte[] decodedPrivateKey = Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(decodedPrivateKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        this.privateKey = keyFactory.generatePrivate(privateKeySpec);

        // Public Key 로드
        byte[] publicKeyBytes = publicKeyResource.getInputStream().readAllBytes();
        String publicKeyPEM = new String(publicKeyBytes, StandardCharsets.UTF_8)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "")
                .trim();
        byte[] decodedPublicKey = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(decodedPublicKey);
        this.publicKey = keyFactory.generatePublic(publicKeySpec);
    }

    // JWT 만들어주는 함수
    public String createAccessToken(Authentication auth) {

        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        Member member = userDetails.getMember();

        Set<String> roleSet = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // 원하는 정보만 포함한 DTO 생성
        MemberDto memberDto = MemberDto.builder()
                .id(member.getId())
                .username(member.getUsername())
                .displayName(member.getDisplayName())
                .roleSet(roleSet)
                .build();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String memberJson = "";
        try {
            memberJson = objectMapper.writeValueAsString(memberDto);
        } catch (JsonProcessingException e) {
            throw new GlobalException("사용자 정보를 JSON으로 변환하는 중 오류가 발생했습니다", "JSON_CONVERSION_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return Jwts.builder()
                .setSubject(member.getUsername())
                .claim("userInfo", memberJson) // JSON 형태로 저장
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 20000))
                .signWith(privateKey)
                .compact();
    }

    //    jwt재발급 해주는 메소드-> AccessToken이 만료되면 Authentication auth는 무효가 되기때문에 username으로 사용자 정보를 로드
    public String refreshAccessToken(String username) {

        CustomUserDetails userDetails = (CustomUserDetails) customUserDetailsService.loadUserByUsername(username);

        // 사용자 정보를 기반으로 Authentication 객체 생성 (패스워드는 null로 설정)
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        // 이제 기존의 createAccessToken(Authentication auth) 로직을 재사용할 수 있음
        CustomUserDetails customUserDetails = (CustomUserDetails) auth.getPrincipal();
        Member member = customUserDetails.getMember();

        Set<String> roleSet = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        MemberDto memberDto = MemberDto.builder()
                .id(member.getId())
                .username(member.getUsername())
                .displayName(member.getDisplayName())
                .roleSet(roleSet)
                .build();

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        String userInfoJson = "";
        try {
            userInfoJson = objectMapper.writeValueAsString(memberDto);
        } catch (JsonProcessingException e) {
            throw new GlobalException("사용자 정보를 JSON으로 변환하는 중 오류가 발생했습니다", "JSON_CONVERSION_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return Jwts.builder()
                .setSubject(member.getUsername())
                .claim("userInfo", userInfoJson)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 20000))
                .signWith(privateKey)
                .compact();
    }

    public String createRefreshToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 604800000)) // 7일 유효
                .signWith(privateKey)
                .compact();
    }

    //JWT 토큰에서 클레임(Claims)을 추출하는 기능을 수행
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    //이 메서드는 JWT 토큰이 만료되었는지 여부를 확인하는 기능을 수행
    public boolean isTokenExpired(String token) {
        try {
            final Date expiration = extractClaims(token).getExpiration();
            return expiration.before(new Date());
        } catch (ExpiredJwtException e) {
            log.debug("토큰이 만료되었습니다: {}", e.getMessage());
            return true;
        } catch (Exception e) {
            log.warn("토큰 검증 중 오류 발생: {}", e.getMessage());
            return true; // 오류 났으면 만료된 걸로 간주
        }
    }

    //이 메서드는 JWT 토큰에서 사용자 이름을 추출하는 기능을 수행
    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    /**
     * RefreshToken Rotation 구현
     * 1. 레디스에서 formerRefreshToken이 있는지 확인
     * 2. 있으면 기존 토큰을 만료시키고 새로운 refreshToken을 레디스에 등록
     * 3. TTL은 createRefreshToken의 날짜와 동일하게 설정 (7일)
     *
     * @param formerRefreshToken 이전 리프레시 토큰
     * @param username 사용자명
     * @return 새로운 리프레시 토큰
     */
    public String refreshTokenRotation(String formerRefreshToken, String username) {
        String refreshTokenKey = "refresh_token:" + username;

        try {
            // 1. 레디스에서 기존 refreshToken이 존재하고, 유효한지 확인
            if(!isValidRefreshToken(username, formerRefreshToken)){
                redisService.deleteValue(refreshTokenKey);
                throw new GlobalException("저장된 리프레시 토큰이 없거나 유효하지 않습니다", "INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED);
            }
            // 2. 새로운 refreshToken 생성
            String newRefreshToken = createRefreshToken(username);
            // 3. 기존 토큰 만료 및 새 토큰 저장 (TTL: 7일 = 604800초)
            redisService.setValue(refreshTokenKey, newRefreshToken, 604800, TimeUnit.SECONDS);

            return newRefreshToken;

        } catch (Exception e) {
            // 오류 발생 시 기존 토큰 삭제 (보안 강화)
            redisService.deleteValue(refreshTokenKey);
            if (e instanceof GlobalException) {
                throw e;
            }
            throw new GlobalException("리프레시 토큰 로테이션 중 오류가 발생했습니다", "REFRESH_TOKEN_ROTATION_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 로그인 시 RefreshToken을 Redis에 저장
     *
     * @param username 사용자명
     * @param refreshToken 리프레시 토큰
     */
    public void storeRefreshToken(String username, String refreshToken) {
        String refreshTokenKey = "refresh_token:" + username;
        // TTL: 7일 (createRefreshToken과 동일)
        redisService.setValue(refreshTokenKey, refreshToken, 604800, TimeUnit.SECONDS);
    }

    /**
     * 로그아웃 시 RefreshToken을 Redis에서 삭제
     *
     * @param username 사용자명
     */
    public void removeRefreshToken(String username) {
        String refreshTokenKey = "refresh_token:" + username;
        redisService.deleteValue(refreshTokenKey);
    }

    /**
     * RefreshToken이 Redis에 저장되어 있는지 확인
     *
     * @param username 사용자명
     * @param refreshToken 확인할 리프레시 토큰
     * @return 유효성 여부
     */
    private boolean isValidRefreshToken(String username, String refreshToken) {
        String refreshTokenKey = "refresh_token:" + username;
        Object storedToken = redisService.getValue(refreshTokenKey);

        return storedToken != null && refreshToken.equals(storedToken.toString());
    }

}