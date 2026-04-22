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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthService 단위 테스트")
class OAuthServiceTest {

    @Mock
    private OAuthProviderClient googleClient;

    @Mock
    private OAuthProviderClient kakaoClient;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RedisService redisService;

    private OAuthService oauthService;

    // ── 공통 픽스처 ─────────────────────────────────────────────────

    private static final String CODE = "auth-code";
    private static final String STATE = "state-xyz";
    private static final String STATE_KEY = "OAUTH_STATE:" + STATE;
    private static final String PROVIDER_ACCESS_TOKEN = "provider.access.token";
    private static final String PROVIDER_ID = "12345";
    private static final String EMAIL = "user@gmail.com";
    private static final String NICKNAME = "사용자";
    private static final String EXPECTED_USERNAME = "google_12345";
    private static final String JWT_ACCESS = "jym.access.token";
    private static final String JWT_REFRESH = "jym.refresh.token";

    @BeforeEach
    void setUp() {
        given(googleClient.getProvider()).willReturn(AuthProvider.GOOGLE);
        given(kakaoClient.getProvider()).willReturn(AuthProvider.KAKAO);

        oauthService = new OAuthService(
                List.of(googleClient, kakaoClient),
                memberRepository,
                jwtProvider,
                redisService);
    }

    private OAuthTokenResponse providerToken() {
        return OAuthTokenResponse.builder().accessToken(PROVIDER_ACCESS_TOKEN).build();
    }

    private OAuthUserInfo providerUserInfo() {
        return OAuthUserInfo.builder()
                .provider(AuthProvider.GOOGLE)
                .providerId(PROVIDER_ID)
                .email(EMAIL)
                .nickname(NICKNAME)
                .build();
    }

    private Member existingMember() {
        return Member.builder()
                .id(10L)
                .username(EXPECTED_USERNAME)
                .password(null)
                .email(EMAIL)
                .nickname(NICKNAME)
                .role(Role.ROLE_USER)
                .authProvider(AuthProvider.GOOGLE)
                .providerId(PROVIDER_ID)
                .isActive(true)
                .build();
    }

    private void stubValidState() {
        given(redisService.getValue(STATE_KEY)).willReturn(AuthProvider.GOOGLE.name());
    }

    private void stubProviderFlow() {
        given(googleClient.getAccessToken(CODE)).willReturn(providerToken());
        given(googleClient.getUserInfo(PROVIDER_ACCESS_TOKEN)).willReturn(providerUserInfo());
    }

    // ── buildAuthorizationUrl() ─────────────────────────────────────

    @Nested
    @DisplayName("buildAuthorizationUrl()")
    class BuildAuthorizationUrl {

        @Test
        @DisplayName("AU-01: 유효 provider → 인가 URL 반환 + Redis에 state 저장")
        void buildAuthorizationUrl_validProvider_returnsUrlAndStoresState() {
            given(googleClient.getAuthorizationUrl(anyString())).willReturn("https://google/auth?state=xx");

            String url = oauthService.buildAuthorizationUrl("google");

            assertThat(url).startsWith("https://google/auth");
            verify(redisService).setValue(
                    anyString(),
                    eq(AuthProvider.GOOGLE.name()),
                    any(Duration.class));
        }

        @Test
        @DisplayName("AU-02: 미지원 provider → ERR_UNSUPPORTED_PROVIDER (BAD_REQUEST)")
        void buildAuthorizationUrl_unsupportedProvider_throws() {
            assertThatThrownBy(() -> oauthService.buildAuthorizationUrl("naver"))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> {
                        GlobalException ge = (GlobalException) ex;
                        assertThat(ge.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ge.getErrorCode()).isEqualTo("ERR_UNSUPPORTED_PROVIDER");
                    });
        }

        @Test
        @DisplayName("AU-03: LOCAL provider → ERR_UNSUPPORTED_PROVIDER")
        void buildAuthorizationUrl_localProvider_throws() {
            assertThatThrownBy(() -> oauthService.buildAuthorizationUrl("local"))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> assertThat(((GlobalException) ex).getErrorCode())
                            .isEqualTo("ERR_UNSUPPORTED_PROVIDER"));
        }
    }

    // ── processCallback() ────────────────────────────────────────────

    @Nested
    @DisplayName("processCallback()")
    class ProcessCallback {

        @Test
        @DisplayName("OS-01: 신규 사용자 → Member 저장 + JWT 발급")
        void processCallback_newUser_createsMemberAndReturnsToken() {
            stubValidState();
            stubProviderFlow();
            given(memberRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, PROVIDER_ID))
                    .willReturn(Optional.empty());
            given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));
            given(jwtProvider.createAccessToken(any(), anyString(), anyString(), anyString()))
                    .willReturn(JWT_ACCESS);
            given(jwtProvider.createRefreshToken(anyString())).willReturn(JWT_REFRESH);

            Map<String, String> tokens = oauthService.processCallback("google", CODE, STATE);

            assertThat(tokens.get("accessToken")).isEqualTo(JWT_ACCESS);
            assertThat(tokens.get("refreshToken")).isEqualTo(JWT_REFRESH);
            verify(memberRepository, times(1)).save(any(Member.class));
        }

        @Test
        @DisplayName("OS-02: 기존 사용자 → save 미호출, JWT 발급")
        void processCallback_existingUser_reusesMember() {
            stubValidState();
            stubProviderFlow();
            given(memberRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, PROVIDER_ID))
                    .willReturn(Optional.of(existingMember()));
            given(jwtProvider.createAccessToken(any(), anyString(), anyString(), anyString()))
                    .willReturn(JWT_ACCESS);
            given(jwtProvider.createRefreshToken(anyString())).willReturn(JWT_REFRESH);

            Map<String, String> tokens = oauthService.processCallback("google", CODE, STATE);

            assertThat(tokens.get("accessToken")).isEqualTo(JWT_ACCESS);
            verify(memberRepository, never()).save(any(Member.class));
        }

        @Test
        @DisplayName("OS-03: Provider 토큰 교환 실패 → GlobalException 전파")
        void processCallback_tokenExchangeFails_throws() {
            stubValidState();
            given(googleClient.getAccessToken(CODE))
                    .willThrow(new GlobalException(
                            "Google 토큰 교환에 실패했습니다.",
                            "ERR_OAUTH_TOKEN_EXCHANGE",
                            HttpStatus.UNAUTHORIZED));

            assertThatThrownBy(() -> oauthService.processCallback("google", CODE, STATE))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> assertThat(((GlobalException) ex).getErrorCode())
                            .isEqualTo("ERR_OAUTH_TOKEN_EXCHANGE"));
        }

        @Test
        @DisplayName("OS-04: UserInfo 조회 실패 → GlobalException 전파")
        void processCallback_userInfoFails_throws() {
            stubValidState();
            given(googleClient.getAccessToken(CODE)).willReturn(providerToken());
            given(googleClient.getUserInfo(PROVIDER_ACCESS_TOKEN))
                    .willThrow(new GlobalException(
                            "Google 사용자 정보 조회에 실패했습니다.",
                            "ERR_OAUTH_USER_INFO",
                            HttpStatus.UNAUTHORIZED));

            assertThatThrownBy(() -> oauthService.processCallback("google", CODE, STATE))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> assertThat(((GlobalException) ex).getErrorCode())
                            .isEqualTo("ERR_OAUTH_USER_INFO"));
        }

        @Test
        @DisplayName("OS-05: 비활성 계정 → FORBIDDEN 예외")
        void processCallback_inactiveMember_throwsForbidden() {
            stubValidState();
            stubProviderFlow();
            Member inactive = Member.builder()
                    .id(10L)
                    .username(EXPECTED_USERNAME)
                    .nickname(NICKNAME)
                    .email(EMAIL)
                    .role(Role.ROLE_USER)
                    .authProvider(AuthProvider.GOOGLE)
                    .providerId(PROVIDER_ID)
                    .isActive(false)
                    .build();
            given(memberRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, PROVIDER_ID))
                    .willReturn(Optional.of(inactive));

            assertThatThrownBy(() -> oauthService.processCallback("google", CODE, STATE))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> assertThat(((GlobalException) ex).getHttpStatus())
                            .isEqualTo(HttpStatus.FORBIDDEN));
        }

        @Test
        @DisplayName("OS-06: JWT AT/RT 각 1회 호출 확인")
        void processCallback_jwtIssuedOnce() {
            stubValidState();
            stubProviderFlow();
            given(memberRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, PROVIDER_ID))
                    .willReturn(Optional.of(existingMember()));
            given(jwtProvider.createAccessToken(any(), anyString(), anyString(), anyString()))
                    .willReturn(JWT_ACCESS);
            given(jwtProvider.createRefreshToken(anyString())).willReturn(JWT_REFRESH);

            oauthService.processCallback("google", CODE, STATE);

            verify(jwtProvider, times(1)).createAccessToken(any(), anyString(), anyString(), anyString());
            verify(jwtProvider, times(1)).createRefreshToken(EXPECTED_USERNAME);
        }

        @Test
        @DisplayName("OS-08~10: 자동 가입 시 username/password/role 기본값 검증")
        void processCallback_autoRegister_fieldsCorrect() {
            stubValidState();
            stubProviderFlow();
            given(memberRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, PROVIDER_ID))
                    .willReturn(Optional.empty());
            given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));
            given(jwtProvider.createAccessToken(any(), anyString(), anyString(), anyString()))
                    .willReturn(JWT_ACCESS);
            given(jwtProvider.createRefreshToken(anyString())).willReturn(JWT_REFRESH);

            oauthService.processCallback("google", CODE, STATE);

            ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
            verify(memberRepository).save(captor.capture());
            Member saved = captor.getValue();
            assertThat(saved.getUsername()).isEqualTo(EXPECTED_USERNAME);
            assertThat(saved.getPassword()).isNull();
            assertThat(saved.getRole()).isEqualTo(Role.ROLE_USER);
            assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
            assertThat(saved.getProviderId()).isEqualTo(PROVIDER_ID);
            assertThat(saved.getIsActive()).isTrue();
        }
    }

    // ── State 검증 테스트 ───────────────────────────────────────────

    @Nested
    @DisplayName("State 검증")
    class StateValidation {

        @Test
        @DisplayName("ST-01: 유효한 state → Redis에서 삭제")
        void state_valid_deletedFromRedis() {
            stubValidState();
            stubProviderFlow();
            given(memberRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, PROVIDER_ID))
                    .willReturn(Optional.of(existingMember()));
            given(jwtProvider.createAccessToken(any(), anyString(), anyString(), anyString()))
                    .willReturn(JWT_ACCESS);
            given(jwtProvider.createRefreshToken(anyString())).willReturn(JWT_REFRESH);

            oauthService.processCallback("google", CODE, STATE);

            verify(redisService).deleteValue(STATE_KEY);
        }

        @Test
        @DisplayName("ST-02: 존재하지 않는 state → ERR_OAUTH_INVALID_STATE")
        void state_missing_throws() {
            given(redisService.getValue(STATE_KEY)).willReturn(null);

            assertThatThrownBy(() -> oauthService.processCallback("google", CODE, STATE))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> {
                        GlobalException ge = (GlobalException) ex;
                        assertThat(ge.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(ge.getErrorCode()).isEqualTo("ERR_OAUTH_INVALID_STATE");
                    });
        }

        @Test
        @DisplayName("ST-03: provider 불일치 → ERR_OAUTH_INVALID_STATE")
        void state_providerMismatch_throws() {
            given(redisService.getValue(STATE_KEY)).willReturn(AuthProvider.KAKAO.name());

            assertThatThrownBy(() -> oauthService.processCallback("google", CODE, STATE))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> assertThat(((GlobalException) ex).getErrorCode())
                            .isEqualTo("ERR_OAUTH_INVALID_STATE"));
        }
    }
}
