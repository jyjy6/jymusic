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

@DisplayName("GoogleOAuthClient 단위 테스트")
class GoogleOAuthClientTest {

    private static final String CLIENT_ID = "google-client-id";
    private static final String CLIENT_SECRET = "google-client-secret";
    private static final String REDIRECT_URI = "http://localhost:8080/api/v1/auth/oauth2/callback/google";
    private static final String AUTH_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";

    private GoogleOAuthClient client;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        OAuth2Properties.Provider provider = new OAuth2Properties.Provider();
        provider.setClientId(CLIENT_ID);
        provider.setClientSecret(CLIENT_SECRET);
        provider.setRedirectUri(REDIRECT_URI);
        provider.setAuthorizationUri(AUTH_URI);
        provider.setTokenUri(TOKEN_URI);
        provider.setUserInfoUri(USERINFO_URI);
        provider.setScopes("openid,email,profile");

        OAuth2Properties properties = new OAuth2Properties();
        properties.setProviders(new HashMap<>());
        properties.getProviders().put("google", provider);

        client = new GoogleOAuthClient(restClient, properties);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("GC-05: getProvider() → AuthProvider.GOOGLE")
    void getProvider_returnsGoogle() {
        assertThat(client.getProvider()).isEqualTo(AuthProvider.GOOGLE);
    }

    @Test
    @DisplayName("GC-01: 인가 URL 생성 → 필수 파라미터 모두 포함")
    void getAuthorizationUrl_includesRequiredParams() {
        String url = client.getAuthorizationUrl("test-state");

        assertThat(url).startsWith(AUTH_URI);
        assertThat(url).contains("client_id=" + CLIENT_ID);
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("state=test-state");
        assertThat(url).contains("scope=");
        assertThat(url).contains("redirect_uri=");
    }

    @Test
    @DisplayName("GC-02: Access Token 취득 성공 → OAuthTokenResponse 반환")
    void getAccessToken_success_returnsToken() {
        mockServer.expect(requestTo(TOKEN_URI))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"access_token\":\"ya29.test-token\",\"token_type\":\"Bearer\",\"expires_in\":3599}",
                        MediaType.APPLICATION_JSON));

        OAuthTokenResponse response = client.getAccessToken("auth-code");

        assertThat(response.getAccessToken()).isEqualTo("ya29.test-token");
        mockServer.verify();
    }

    @Test
    @DisplayName("GC-03: Access Token 취득 실패(4xx) → ERR_OAUTH_TOKEN_EXCHANGE")
    void getAccessToken_failure_throwsGlobalException() {
        mockServer.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest().body("{\"error\":\"invalid_grant\"}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getAccessToken("bad-code"))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getErrorCode())
                        .isEqualTo("ERR_OAUTH_TOKEN_EXCHANGE"));
    }

    @Test
    @DisplayName("GC-04: UserInfo 조회 성공 → providerId, email, nickname 파싱")
    void getUserInfo_success_returnsUserInfo() {
        mockServer.expect(requestTo(USERINFO_URI))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess(
                        "{\"sub\":\"1234567890\",\"email\":\"jane@example.com\",\"name\":\"Jane Doe\"}",
                        MediaType.APPLICATION_JSON));

        OAuthUserInfo info = client.getUserInfo("access-token");

        assertThat(info.getProviderId()).isEqualTo("1234567890");
        assertThat(info.getEmail()).isEqualTo("jane@example.com");
        assertThat(info.getNickname()).isEqualTo("Jane Doe");
        assertThat(info.getProvider()).isEqualTo(AuthProvider.GOOGLE);
    }

    @Test
    @DisplayName("GC-06: UserInfo 조회 실패 → ERR_OAUTH_USER_INFO")
    void getUserInfo_failure_throwsGlobalException() {
        mockServer.expect(requestTo(USERINFO_URI))
                .andRespond(withBadRequest());

        assertThatThrownBy(() -> client.getUserInfo("bad-token"))
                .isInstanceOf(GlobalException.class)
                .satisfies(ex -> assertThat(((GlobalException) ex).getErrorCode())
                        .isEqualTo("ERR_OAUTH_USER_INFO"));
    }
}
