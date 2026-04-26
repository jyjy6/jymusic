package jymusic.jym_member_auth_service.controller.member;

import jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_member_auth_service.config.SpringSecurityConfig;
import jymusic.jym_member_auth_service.domain.member.Role;
import jymusic.jym_member_auth_service.dto.member.InternalMemberSummaryResponse;
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

import java.util.List;

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

    @Nested
    @DisplayName("내부 조회 API")
    class InternalMemberApis {

        @Test
        @DisplayName("GET /api/v1/members/search?keyword=... -> 200")
        void searchMembers_returns200() throws Exception {
            given(memberService.searchMembers("tester"))
                    .willReturn(List.of(InternalMemberSummaryResponse.builder()
                            .memberId(1L)
                            .username("tester")
                            .nickname("테스터")
                            .email("tester@example.com")
                            .build()));

            mockMvc.perform(get("/api/v1/members/search")
                            .param("keyword", "tester"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].memberId").value(1L))
                    .andExpect(jsonPath("$[0].username").value("tester"));
        }

        @Test
        @DisplayName("GET /api/v1/members/batch?ids=1,2 -> 200")
        void batchMembers_returns200() throws Exception {
            given(memberService.getMembersBatch(List.of(1L, 2L)))
                    .willReturn(List.of(
                            InternalMemberSummaryResponse.builder().memberId(1L).username("u1").nickname("n1").email("u1@test.com").build(),
                            InternalMemberSummaryResponse.builder().memberId(2L).username("u2").nickname("n2").email("u2@test.com").build()
                    ));

            mockMvc.perform(get("/api/v1/members/batch")
                            .param("ids", "1,2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].memberId").value(1L))
                    .andExpect(jsonPath("$[1].memberId").value(2L));
        }

        @Test
        @DisplayName("GET /api/v1/members/{memberId} -> 200")
        void getMemberById_returns200() throws Exception {
            given(memberService.getMemberById(10L))
                    .willReturn(InternalMemberSummaryResponse.builder()
                            .memberId(10L)
                            .username("u10")
                            .nickname("n10")
                            .email("u10@test.com")
                            .build());

            mockMvc.perform(get("/api/v1/members/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.memberId").value(10L))
                    .andExpect(jsonPath("$.username").value("u10"));
        }
    }
}
