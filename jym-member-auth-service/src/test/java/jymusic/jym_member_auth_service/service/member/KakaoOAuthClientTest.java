package jymusic.jym_member_auth_service.service.member;

import jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_member_auth_service.config.OAuth2Properties;
import jymusic.jym_member_auth_service.domain.member.AuthProvider;
import jymusic.jym_member_auth_service.dto.member.OAuthTokenResponse;
import jymusic.jym_member_auth_service.dto.member.OAuthUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("KakaoOAuthClient 단위 테스트")
class KakaoOAuthClientTest {

    private static final String CLIENT_ID = "kakao-client-id";
    private static final String REDIRECT_URI = "http://localhost:8080/api/v1/auth/oauth2/callback/kakao";
    private static final String AUTH_URI = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USERINFO_URI = "https://kapi.kakao.com/v2/user/me";

    private KakaoOAuthClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        OAuth2Properties.Provider provider = new OAuth2Properties.Provider();
        provider.setClientId(CLIENT_ID);
        provider.setClientSecret("");
        provider.setRedirectUri(REDIRECT_URI);
        provider.setAuthorizationUri(AUTH_URI);
        provider.setTokenUri(TOKEN_URI);
        provider.setUserInfoUri(USERINFO_URI);
        provider.setScopes("profile_nickname,account_email");

        OAuth2Properties properties = new OAuth2Properties();
        properties.setProviders(new HashMap<>());
        properties.getProviders().put("kakao", provider);

        client = new KakaoOAuthClient(restClient, properties);
    }

    @Test
    @DisplayName("KC-05: getProvider() → AuthProvider.KAKAO")
    void getProvider_returnsKakao() {
        assertThat(client.getProvider()).isEqualTo(AuthProvider.KAKAO);
    }

    @Test
    @DisplayName("KC-01: 인가 URL 생성 → 필수 파라미터 모두 포함")
    void getAuthorizationUrl_includesRequiredParams() {
        String url = client.getAuthorizationUrl("test-state");

        assertThat(url).startsWith(AUTH_URI);
        assertThat(url).contains("client_id=" + CLIENT_ID);
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("state=test-state");
        assertThat(url).contains("scope=");
    }

    @Test
    @DisplayName("KC-02: Access Token 취득 성공 → OAuthTokenResponse 반환")
    void getAccessToken_success_returnsToken() {
        mockServer.expect(requestTo(TOKEN_URI))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"kakao-test-token\",\"token_type\":\"bearer\",\"expires_in\":21599}",
                        MediaType.APPLICATION_JSON));

        OAuthTokenResponse response = client.getAccessToken("auth-code");

        assertThat(response.getAccessToken()).isEqualTo("kakao-test-token");
        mockServer.verify();
    }

    @Test
    @DisplayName("KC-03: Access Token 취득 실패(4xx) → ERR_OAUTH_TOKEN_EXCHANGE")
    void getAccessToken_failure_throwsGlobalException() {
        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest());

        assertThatThrownBy(() -> client.getAccessToken("bad-code"))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getErrorCode())
                        .isEqualTo("ERR_OAUTH_TOKEN_EXCHANGE"));
    }

    @Test
    @DisplayName("KC-04: UserInfo 조회 성공 → id/email/nickname 파싱 (Kakao 포맷)")
    void getUserInfo_success_returnsUserInfo() {
        String kakaoBody = """
                {
                  "id": 9876543210,
                  "kakao_account": {
                    "email": "kakao@example.com",
                    "profile": {"nickname": "카카오닉"}
                  },
                  "properties": {"nickname": "카카오닉"}
                }
                """;

        mockServer.expect(requestTo(USERINFO_URI))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess(kakaoBody, MediaType.APPLICATION_JSON));

        OAuthUserInfo info = client.getUserInfo("access-token");

        assertThat(info.getProviderId()).isEqualTo("9876543210");
        assertThat(info.getEmail()).isEqualTo("kakao@example.com");
        assertThat(info.getNickname()).isEqualTo("카카오닉");
        assertThat(info.getProvider()).isEqualTo(AuthProvider.KAKAO);
    }
}
