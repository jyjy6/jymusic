package jymusic.jym_member_auth_service.service.member;

import jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_member_auth_service.common.jwt.JwtProvider;
import jymusic.jym_member_auth_service.domain.member.AuthProvider;
import jymusic.jym_member_auth_service.domain.member.Member;
import jymusic.jym_member_auth_service.domain.member.MemberRepository;
import jymusic.jym_member_auth_service.domain.member.Role;
import jymusic.jym_member_auth_service.dto.member.MemberLoginRequest;
import jymusic.jym_member_auth_service.dto.member.MemberProfileResponse;
import jymusic.jym_member_auth_service.dto.member.MemberRegistrationRequest;
import jymusic.jym_member_auth_service.dto.member.InternalMemberSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberService 단위 테스트")
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private PasswordEncoder passwordEncoder;

    // ── 공통 픽스처 ─────────────────────────────────────────────────

    private static final Long   MEMBER_ID        = 1L;
    private static final String USERNAME         = "testuser";
    private static final String RAW_PASSWORD     = "rawpass123";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedBcryptHash";
    private static final String NICKNAME         = "테스트유저";
    private static final String EMAIL            = "test@example.com";
    private static final String ACCESS_TOKEN     = "mock.access.token";
    private static final String REFRESH_TOKEN    = "mock.refresh.token";

    private Member activeMember() {
        return Member.builder()
                .id(MEMBER_ID)
                .username(USERNAME)
                .password(ENCODED_PASSWORD)
                .nickname(NICKNAME)
                .email(EMAIL)
                .role(Role.ROLE_USER)
                .authProvider(AuthProvider.LOCAL)
                .isActive(true)
                .build();
    }

    private MemberRegistrationRequest registrationRequest() {
        return MemberRegistrationRequest.builder()
                .username(USERNAME)
                .password(RAW_PASSWORD)
                .nickname(NICKNAME)
                .email(EMAIL)
                .build();
    }

    private MemberLoginRequest loginRequest() {
        return MemberLoginRequest.builder()
                .username(USERNAME)
                .password(RAW_PASSWORD)
                .build();
    }

    // ── register() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("RS-01: 유효한 요청 → MemberProfileResponse 반환")
        void register_validRequest_returnsMemberProfileResponse() {
            given(memberRepository.existsByUsername(USERNAME)).willReturn(false);
            given(memberRepository.existsByEmail(EMAIL)).willReturn(false);
            given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);
            given(memberRepository.save(any(Member.class))).willReturn(activeMember());

            MemberProfileResponse response = memberService.register(registrationRequest());

            assertThat(response.getUsername()).isEqualTo(USERNAME);
            assertThat(response.getNickname()).isEqualTo(NICKNAME);
            assertThat(response.getEmail()).isEqualTo(EMAIL);
        }

        @Test
        @DisplayName("RS-02: 중복 username → CONFLICT 예외 발생, save 미호출")
        void register_duplicateUsername_throwsConflict() {
            given(memberRepository.existsByUsername(USERNAME)).willReturn(true);

            assertThatThrownBy(() -> memberService.register(registrationRequest()))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> {
                        GlobalException ge = (GlobalException) ex;
                        assertThat(ge.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(ge.getErrorCode()).isEqualTo("ERR_DUPLICATE_USERNAME");
                    });

            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("RS-03: 중복 email → CONFLICT 예외 발생, save 미호출")
        void register_duplicateEmail_throwsConflict() {
            given(memberRepository.existsByUsername(USERNAME)).willReturn(false);
            given(memberRepository.existsByEmail(EMAIL)).willReturn(true);

            assertThatThrownBy(() -> memberService.register(registrationRequest()))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> {
                        GlobalException ge = (GlobalException) ex;
                        assertThat(ge.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(ge.getErrorCode()).isEqualTo("ERR_DUPLICATE_EMAIL");
                    });

            verify(memberRepository, never()).save(any());
        }

        @Test
        @DisplayName("RS-04: passwordEncoder.encode() 가 원본 비밀번호로 호출됨")
        void register_passwordEncodeCalledWithRawPassword() {
            given(memberRepository.existsByUsername(USERNAME)).willReturn(false);
            given(memberRepository.existsByEmail(EMAIL)).willReturn(false);
            given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);
            given(memberRepository.save(any(Member.class))).willReturn(activeMember());

            memberService.register(registrationRequest());

            verify(passwordEncoder).encode(RAW_PASSWORD);
        }
    }

    // ── login() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("LS-01: 유효한 자격증명 → accessToken + refreshToken 반환")
        void login_validCredentials_returnsBothTokens() {
            given(memberRepository.findByUsername(USERNAME)).willReturn(Optional.of(activeMember()));
            given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
            given(jwtProvider.createAccessToken(MEMBER_ID, USERNAME, Role.ROLE_USER.name(), NICKNAME))
                    .willReturn(ACCESS_TOKEN);
            given(jwtProvider.createRefreshToken(USERNAME)).willReturn(REFRESH_TOKEN);

            Map<String, String> tokens = memberService.login(loginRequest());

            assertThat(tokens.get("accessToken")).isEqualTo(ACCESS_TOKEN);
            assertThat(tokens.get("refreshToken")).isEqualTo(REFRESH_TOKEN);
        }

        @Test
        @DisplayName("LS-02: 존재하지 않는 username → UNAUTHORIZED 예외 발생")
        void login_usernameNotFound_throwsUnauthorized() {
            given(memberRepository.findByUsername(USERNAME)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.login(loginRequest()))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> assertThat(((GlobalException) ex).getHttpStatus())
                            .isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        @Test
        @DisplayName("LS-03: 비밀번호 불일치 → UNAUTHORIZED 예외 발생")
        void login_wrongPassword_throwsUnauthorized() {
            given(memberRepository.findByUsername(USERNAME)).willReturn(Optional.of(activeMember()));
            given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(false);

            assertThatThrownBy(() -> memberService.login(loginRequest()))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> assertThat(((GlobalException) ex).getHttpStatus())
                            .isEqualTo(HttpStatus.UNAUTHORIZED));
        }

        @Test
        @DisplayName("LS-05: 로그인 성공 시 createRefreshToken() 호출 확인 (Redis 저장 위임)")
        void login_success_createRefreshTokenCalled() {
            given(memberRepository.findByUsername(USERNAME)).willReturn(Optional.of(activeMember()));
            given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
            given(jwtProvider.createAccessToken(any(), anyString(), anyString(), anyString()))
                    .willReturn(ACCESS_TOKEN);
            given(jwtProvider.createRefreshToken(USERNAME)).willReturn(REFRESH_TOKEN);

            memberService.login(loginRequest());

            verify(jwtProvider).createRefreshToken(USERNAME);
        }

        // TODO [spec gap]: LS-04 - is_active=false 계정 로그인 시 FORBIDDEN 반환
        //  현재 MemberService.login()에 is_active 체크 로직이 없음.
        //  스펙(04_TEST_SPEC §4.1 LS-04)에 따라 비즈니스 코드 보완 필요.
    }

    // ── getProfile() ────────────────────────────────────────────────

    @Nested
    @DisplayName("getProfile()")
    class GetProfile {

        @Test
        @DisplayName("MP-01: 존재하는 username → MemberProfileResponse 반환")
        void getProfile_existingUsername_returnsMemberProfileResponse() {
            given(memberRepository.findByUsername(USERNAME)).willReturn(Optional.of(activeMember()));

            MemberProfileResponse response = memberService.getProfile(USERNAME);

            assertThat(response.getUsername()).isEqualTo(USERNAME);
            assertThat(response.getNickname()).isEqualTo(NICKNAME);
            assertThat(response.getId()).isEqualTo(MEMBER_ID);
        }

        @Test
        @DisplayName("MP-02: 존재하지 않는 username → NOT_FOUND 예외 발생")
        void getProfile_memberNotFound_throwsNotFound() {
            given(memberRepository.findByUsername(USERNAME)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.getProfile(USERNAME))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> {
                        GlobalException ge = (GlobalException) ex;
                        assertThat(ge.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(ge.getErrorCode()).isEqualTo("ERR_MEMBER_NOT_FOUND");
                    });
        }
    }

    // ── refreshTokens() ─────────────────────────────────────────────

    @Nested
    @DisplayName("refreshTokens()")
    class RefreshTokens {

        @Test
        @DisplayName("RT-01: 유효한 refreshToken → 새 accessToken + refreshToken 반환")
        void refreshTokens_validToken_returnsNewTokenPair() {
            given(jwtProvider.extractUsername(REFRESH_TOKEN)).willReturn(USERNAME);
            given(jwtProvider.rotateRefreshToken(REFRESH_TOKEN, USERNAME)).willReturn("new.refresh.token");
            given(memberRepository.findByUsername(USERNAME)).willReturn(Optional.of(activeMember()));
            given(jwtProvider.createAccessToken(any(), anyString(), anyString(), anyString()))
                    .willReturn("new.access.token");

            Map<String, String> tokens = memberService.refreshTokens(REFRESH_TOKEN);

            assertThat(tokens.get("accessToken")).isEqualTo("new.access.token");
            assertThat(tokens.get("refreshToken")).isEqualTo("new.refresh.token");
        }

        @Test
        @DisplayName("RT-02: 토큰 회전 후 회원이 없으면 NOT_FOUND 예외 발생")
        void refreshTokens_memberNotFoundAfterRotation_throwsNotFound() {
            given(jwtProvider.extractUsername(REFRESH_TOKEN)).willReturn(USERNAME);
            given(jwtProvider.rotateRefreshToken(REFRESH_TOKEN, USERNAME)).willReturn("new.refresh.token");
            given(memberRepository.findByUsername(USERNAME)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.refreshTokens(REFRESH_TOKEN))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> assertThat(((GlobalException) ex).getHttpStatus())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    // ── logout() ────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("LO-01: 유효한 refreshToken → deleteRefreshToken() 호출")
        void logout_validToken_deletesRefreshToken() {
            given(jwtProvider.extractUsername(REFRESH_TOKEN)).willReturn(USERNAME);

            memberService.logout(REFRESH_TOKEN);

            verify(jwtProvider).deleteRefreshToken(USERNAME);
        }

        @Test
        @DisplayName("LO-02: 파싱 불가 토큰이어도 예외 없이 정상 완료")
        void logout_unparsableToken_completesWithoutException() {
            given(jwtProvider.extractUsername(anyString()))
                    .willThrow(new RuntimeException("token parse failed"));

            memberService.logout("invalid.garbage.token");
            // 예외가 발생하지 않으면 테스트 통과
        }
    }

    @Nested
    @DisplayName("searchMembers()")
    class SearchMembers {

        @Test
        @DisplayName("keyword가 비어있으면 BAD_REQUEST 예외가 발생한다")
        void searchMembers_blankKeyword_throwsBadRequest() {
            assertThatThrownBy(() -> memberService.searchMembers(" "))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> {
                        GlobalException ge = (GlobalException) ex;
                        assertThat(ge.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(ge.getErrorCode()).isEqualTo("ERR_MISSING_PARAMETER");
                    });
        }

        @Test
        @DisplayName("keyword 검색 결과를 InternalMemberSummaryResponse 리스트로 반환한다")
        void searchMembers_validKeyword_returnsSummaryList() {
            given(memberRepository.findTop50ByUsernameContainingIgnoreCaseOrNicknameContainingIgnoreCase("test", "test"))
                    .willReturn(java.util.List.of(activeMember()));

            java.util.List<InternalMemberSummaryResponse> result = memberService.searchMembers("test");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMemberId()).isEqualTo(MEMBER_ID);
            assertThat(result.get(0).getUsername()).isEqualTo(USERNAME);
        }
    }

    @Nested
    @DisplayName("getMembersBatch()")
    class GetMembersBatch {

        @Test
        @DisplayName("ids가 비어있으면 빈 리스트를 반환한다")
        void getMembersBatch_emptyIds_returnsEmptyList() {
            java.util.List<InternalMemberSummaryResponse> result = memberService.getMembersBatch(Set.of());
            assertThat(result).isEmpty();
            verify(memberRepository, never()).findByIdIn(any());
        }

        @Test
        @DisplayName("ids로 회원을 조회해 요약 리스트로 반환한다")
        void getMembersBatch_withIds_returnsSummaryList() {
            given(memberRepository.findByIdIn(Set.of(MEMBER_ID))).willReturn(java.util.List.of(activeMember()));

            java.util.List<InternalMemberSummaryResponse> result = memberService.getMembersBatch(Set.of(MEMBER_ID));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMemberId()).isEqualTo(MEMBER_ID);
        }
    }
}
