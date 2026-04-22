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
 * Kakao OAuth 2.0 Provider 구현체.
 * Endpoint 및 응답 스키마: https://developers.kakao.com/docs/latest/ko/kakaologin/rest-api
 */
@Slf4j
@Component
public class KakaoOAuthClient implements OAuthProviderClient {

    private final RestClient restClient;
    private final OAuth2Properties.Provider config;

    public KakaoOAuthClient(RestClient oauthRestClient, OAuth2Properties properties) {
        this.restClient = oauthRestClient;
        this.config = properties.getProviders().get("kakao");
        if (this.config == null) {
            throw new IllegalStateException("oauth2.providers.kakao 설정이 누락되었습니다.");
        }
    }

    @Override
    public AuthProvider getProvider() {
        return AuthProvider.KAKAO;
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
        form.add("grant_type", "authorization_code");
        form.add("client_id", config.getClientId());
        if (config.getClientSecret() != null && !config.getClientSecret().isBlank()) {
            form.add("client_secret", config.getClientSecret());
        }
        form.add("redirect_uri", config.getRedirectUri());
        form.add("code", code);

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
                        "Kakao 토큰 교환에 실패했습니다.",
                        "ERR_OAUTH_TOKEN_EXCHANGE",
                        HttpStatus.UNAUTHORIZED);
            }
            return response;
        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("Kakao token exchange failed: {}", e.getMessage());
            throw new GlobalException(
                    "Kakao 토큰 교환에 실패했습니다.",
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

            if (body == null || body.get("id") == null) {
                throw new GlobalException(
                        "Kakao 사용자 정보 조회에 실패했습니다.",
                        "ERR_OAUTH_USER_INFO",
                        HttpStatus.UNAUTHORIZED);
            }

            String providerId = String.valueOf(body.get("id"));
            Map<String, Object> kakaoAccount = (Map<String, Object>) body.getOrDefault("kakao_account", Map.of());
            Map<String, Object> properties = (Map<String, Object>) body.getOrDefault("properties", Map.of());

            String email = asString(kakaoAccount.get("email"));

            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.getOrDefault("profile", Map.of());
            String nickname = asString(properties.get("nickname"));
            if (nickname == null) {
                nickname = asString(profile.get("nickname"));
            }
            if (nickname == null) {
                nickname = "kakao_" + providerId;
            }

            return OAuthUserInfo.builder()
                    .provider(AuthProvider.KAKAO)
                    .providerId(providerId)
                    .email(email)
                    .nickname(nickname)
                    .build();
        } catch (GlobalException e) {
            throw e;
        } catch (Exception e) {
            log.error("Kakao userinfo fetch failed: {}", e.getMessage());
            throw new GlobalException(
                    "Kakao 사용자 정보 조회에 실패했습니다.",
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
