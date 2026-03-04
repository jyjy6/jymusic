package jymusic.jym_member_auth_service.controller.member;

import jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_member_auth_service.config.SpringSecurityConfig;
import jymusic.jym_member_auth_service.domain.member.Role;
import jymusic.jym_member_auth_service.dto.member.MemberProfileResponse;
import jymusic.jym_member_auth_service.service.member.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberController.class)
@Import(SpringSecurityConfig.class)
@DisplayName("MemberController 단위 테스트")
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    // ── 공통 픽스처 ─────────────────────────────────────────────────

    private static final String GATEWAY_USERNAME = "testuser";

    private MemberProfileResponse sampleProfileResponse() {
        return MemberProfileResponse.builder()
                .id(1L)
                .username(GATEWAY_USERNAME)
                .nickname("테스트유저")
                .email("test@example.com")
                .role(Role.ROLE_USER)
                .build();
    }

    // ── GET /api/v1/members/me ───────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/members/me")
    class GetMyProfile {

        @Test
        @DisplayName("M-01: X-User-Name 헤더 존재 → 200 OK + MemberProfileResponse")
        void getMyProfile_withGatewayHeader_returns200() throws Exception {
            given(memberService.getProfile(GATEWAY_USERNAME)).willReturn(sampleProfileResponse());

            mockMvc.perform(get("/api/v1/members/me")
                            .header("X-User-Name", GATEWAY_USERNAME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(GATEWAY_USERNAME))
                    .andExpect(jsonPath("$.nickname").value("테스트유저"));
        }

        @Test
        @DisplayName("M-02: X-User-Name 헤더 없고 인증 정보 없음 → 401 Unauthorized")
        void getMyProfile_noHeaderAndNoAuth_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/members/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("M-03: 유효한 헤더지만 DB에 회원 없음 → 404 Not Found")
        void getMyProfile_memberNotFoundInDb_returns404() throws Exception {
            given(memberService.getProfile(GATEWAY_USERNAME))
                    .willThrow(new GlobalException(
                            "사용자를 찾을 수 없습니다.", "ERR_MEMBER_NOT_FOUND", HttpStatus.NOT_FOUND));

            mockMvc.perform(get("/api/v1/members/me")
                            .header("X-User-Name", GATEWAY_USERNAME))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ERR_MEMBER_NOT_FOUND"));
        }
    }
}
