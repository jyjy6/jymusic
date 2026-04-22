package jymusic.jym_member_auth_service.service.member;

import jymusic.jym_member_auth_service.domain.member.AuthProvider;
import jymusic.jym_member_auth_service.dto.member.OAuthTokenResponse;
import jymusic.jym_member_auth_service.dto.member.OAuthUserInfo;

/**
 * OAuth Provider별 HTTP 통신을 추상화하는 인터페이스.
 * 구현체는 Google, Kakao 등 Provider마다 하나씩 존재합니다.
 */
public interface OAuthProviderClient {

    /** 이 구현체가 담당하는 Provider */
    AuthProvider getProvider();

    /**
     * Provider의 인가 URL을 생성합니다. 사용자는 이 URL로 302 Redirect 됩니다.
     *
     * @param state CSRF 방지용 state 파라미터
     */
    String getAuthorizationUrl(String state);

    /**
     * Authorization Code를 Provider Access Token으로 교환합니다.
     *
     * @throws jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException
     *         ERR_OAUTH_TOKEN_EXCHANGE
     */
    OAuthTokenResponse getAccessToken(String code);

    /**
     * Provider의 UserInfo API를 호출하여 사용자 정보를 조회합니다.
     *
     * @throws jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException
     *         ERR_OAUTH_USER_INFO
     */
    OAuthUserInfo getUserInfo(String accessToken);
}
