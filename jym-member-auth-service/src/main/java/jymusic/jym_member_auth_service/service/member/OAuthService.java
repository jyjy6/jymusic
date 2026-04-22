package jymusic.jym_member_auth_service.service.member;

import jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_member_auth_service.common.jwt.JwtProvider;
import jymusic.jym_member_auth_service.common.redis.RedisService;
import jymusic.jym_member_auth_service.domain.member.AuthProvider;
import jymusic.jym_member_auth_service.domain.member.Member;
import jymusic.jym_member_auth_service.domain.member.MemberRepository;
import jymusic.jym_member_auth_service.domain.member.Role;
import jymusic.jym_member_auth_service.dto.member.OAuthTokenResponse;
import jymusic.jym_member_auth_service.dto.member.OAuthUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * OAuth 소셜 로그인의 비즈니스 흐름을 오케스트레이션합니다.
 * <ol>
 *   <li>Provider 인가 URL 생성 + state 발급/저장</li>
 *   <li>콜백 수신 시 state 검증 → Access Token 교환 → UserInfo 조회</li>
 *   <li>DB 조회/자동가입 → JWT(AT/RT) 발급</li>
 * </ol>
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class OAuthService {

    private static final String STATE_KEY_PREFIX = "OAUTH_STATE:";
    private static final Duration STATE_TTL = Duration.ofMinutes(5);

    private final Map<AuthProvider, OAuthProviderClient> providerClients;
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final RedisService redisService;

    public OAuthService(
            List<OAuthProviderClient> clients,
            MemberRepository memberRepository,
            JwtProvider jwtProvider,
            RedisService redisService
    ) {
        this.memberRepository = memberRepository;
        this.jwtProvider = jwtProvider;
        this.redisService = redisService;

        this.providerClients = new java.util.EnumMap<>(AuthProvider.class);
        for (OAuthProviderClient client : clients) {
            this.providerClients.put(client.getProvider(), client);
        }
    }

    // ── 1. 인가 URL 생성 (Redirect 시작) ───────────────────────────────

    /**
     * 지정된 Provider의 인가 URL을 생성하고 state를 Redis에 저장합니다.
     *
     * @return Provider 인가 URL (302 Location 헤더에 세팅할 값)
     * @throws GlobalException 지원하지 않는 Provider (400 ERR_UNSUPPORTED_PROVIDER)
     */
    public String buildAuthorizationUrl(String providerName) {
        AuthProvider provider = parseProvider(providerName);
        OAuthProviderClient client = requireClient(provider);

        String state = UUID.randomUUID().toString();
        redisService.setValue(STATE_KEY_PREFIX + state, provider.name(), STATE_TTL);

        return client.getAuthorizationUrl(state);
    }

    // ── 2. 콜백 처리 ─────────────────────────────────────────────────

    /**
     * OAuth 콜백을 처리하여 JWT(AT, RT)를 발급합니다.
     * 반환 Map의 키: accessToken, refreshToken
     */
    @Transactional
    public Map<String, String> processCallback(String providerName, String code, String state) {
        AuthProvider provider = parseProvider(providerName);
        OAuthProviderClient client = requireClient(provider);

        validateAndConsumeState(state, provider);

        OAuthTokenResponse tokenResponse = client.getAccessToken(code);
        OAuthUserInfo userInfo = client.getUserInfo(tokenResponse.getAccessToken());

        Member member = findOrRegisterMember(provider, userInfo);

        if (Boolean.FALSE.equals(member.getIsActive())) {
            throw new GlobalException(
                    "비활성화된 계정입니다.",
                    "ERR_MEMBER_INACTIVE",
                    HttpStatus.FORBIDDEN);
        }

        String accessToken = jwtProvider.createAccessToken(
                member.getId(), member.getUsername(), member.getRole().name(), member.getNickname());
        String refreshToken = jwtProvider.createRefreshToken(member.getUsername());

        return Map.of("accessToken", accessToken, "refreshToken", refreshToken);
    }

    // ── 내부 헬퍼 ─────────────────────────────────────────────────────

    private Member findOrRegisterMember(AuthProvider provider, OAuthUserInfo userInfo) {
        Optional<Member> existing = memberRepository.findByAuthProviderAndProviderId(provider, userInfo.getProviderId());
        if (existing.isPresent()) {
            return existing.get();
        }

        String username = buildUsername(provider, userInfo.getProviderId());
        String nickname = (userInfo.getNickname() != null && !userInfo.getNickname().isBlank())
                ? userInfo.getNickname()
                : username;

        Member newMember = Member.builder()
                .username(username)
                .password(null) // 소셜 로그인은 비밀번호 없음
                .email(userInfo.getEmail())
                .nickname(nickname)
                .role(Role.ROLE_USER)
                .authProvider(provider)
                .providerId(userInfo.getProviderId())
                .isActive(true)
                .build();

        log.info("OAuth 신규 회원 자동 가입 - provider={}, username={}", provider, username);
        return memberRepository.save(newMember);
    }

    private void validateAndConsumeState(String state, AuthProvider expectedProvider) {
        if (state == null || state.isBlank()) {
            throw new GlobalException(
                    "State 파라미터가 누락되었습니다.",
                    "ERR_OAUTH_INVALID_STATE",
                    HttpStatus.UNAUTHORIZED);
        }

        String stored = redisService.getValue(STATE_KEY_PREFIX + state);
        if (stored == null || !stored.equals(expectedProvider.name())) {
            throw new GlobalException(
                    "유효하지 않거나 만료된 state 입니다.",
                    "ERR_OAUTH_INVALID_STATE",
                    HttpStatus.UNAUTHORIZED);
        }

        redisService.deleteValue(STATE_KEY_PREFIX + state);
    }

    private AuthProvider parseProvider(String providerName) {
        if (providerName == null) {
            throw unsupportedProvider(null);
        }
        try {
            AuthProvider provider = AuthProvider.valueOf(providerName.toUpperCase());
            if (provider == AuthProvider.LOCAL) {
                throw unsupportedProvider(providerName);
            }
            return provider;
        } catch (IllegalArgumentException e) {
            throw unsupportedProvider(providerName);
        }
    }

    private OAuthProviderClient requireClient(AuthProvider provider) {
        OAuthProviderClient client = providerClients.get(provider);
        if (client == null) {
            throw unsupportedProvider(provider.name());
        }
        return client;
    }

    private GlobalException unsupportedProvider(String providerName) {
        return new GlobalException(
                "지원하지 않는 OAuth Provider 입니다: " + providerName,
                "ERR_UNSUPPORTED_PROVIDER",
                HttpStatus.BAD_REQUEST);
    }

    private String buildUsername(AuthProvider provider, String providerId) {
        return provider.name().toLowerCase() + "_" + providerId;
    }
}
