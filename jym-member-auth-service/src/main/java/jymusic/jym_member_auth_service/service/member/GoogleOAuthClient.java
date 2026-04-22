package jymusic.jym_member_auth_service.service.member;

import jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_member_auth_service.config.OAuth2Properties;
import jymusic.jym_member_auth_service.domain.member.AuthProvider;
import jymusic.jym_member_auth_service.dto.member.OAuthTokenResponse;
import jymusic.jym_member_auth_service.dto.member.OAuthUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Google OAuth 2.0 Provider 구현체.
 * Endpoint 및 응답 스키마: https://developers.google.com/identity/openid-connect/openid-connect
 */
@Slf4j
@Component
public class GoogleOAuthClient implements OAuthProviderClient {

    private final RestClient restClient;
    private final OAuth2Properties.Provider config;

    public GoogleOAuthClient(RestClient oauthRestClient, OAuth2Properties properties) {
        this.restClient = oauthRestClient;
        this.config = properties.getProviders().get("google");
        if (this.config == null) {
            throw new IllegalStateException("oauth2.providers.google 설정이 누락되었습니다.");
        }
    }

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.GOOGLE;
    }

    @Override
    public String getAuthorizationUrl(String state) {
        return config.getAuthorizationUri()
                + "?client_id=" + encode(config.getClientId())
                + "&redirect_uri=" + encode(config.getRedirectUri())
                + "&response_type=code"
                + "&scope=" + encode(config.getScopes().replace(",", " "))
                + "&state=" + encode(state);
    }

    @Override
    public OAuthTokenResponse getAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", config.getClientId());
        form.add("client_secret", config.getClientSecret());
        form.add("redirect_uri", config.getRedirectUri());
        form.add("grant_type", "authorization_code");

        try {
            OAuthTokenResponse response = restClient.post()
                    .uri(config.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(OAuthTokenResponse.class);

            if (response == null || response.getAccessToken() == null) {
                throw new GlobalException(
                        "Google 토큰 교환에 실패했습니다.",
                        "ERR_OAUTH_TOKEN_EXCHANGE",
                        HttpStatus.UNAUTHORIZED);
            }
            return response;
        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google token exchange failed: {}", e.getMessage());
            throw new GlobalException(
                    "Google 토큰 교환에 실패했습니다.",
                    "ERR_OAUTH_TOKEN_EXCHANGE",
                    HttpStatus.UNAUTHORIZED);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo getUserInfo(String accessToken) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(config.getUserInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);

            if (body == null || body.get("sub") == null) {
                throw new GlobalException(
                        "Google 사용자 정보 조회에 실패했습니다.",
                        "ERR_OAUTH_USER_INFO",
                        HttpStatus.UNAUTHORIZED);
            }

            return OAuthUserInfo.builder()
                    .provider(AuthProvider.GOOGLE)
                    .providerId(String.valueOf(body.get("sub")))
                    .email(asString(body.get("email")))
                    .nickname(asString(body.getOrDefault("name", body.get("email"))))
                    .build();
        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google userinfo fetch failed: {}", e.getMessage());
            throw new GlobalException(
                    "Google 사용자 정보 조회에 실패했습니다.",
                    "ERR_OAUTH_USER_INFO",
                    HttpStatus.UNAUTHORIZED);
        }
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String encode(String v) {
        return v == null ? "" : URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
