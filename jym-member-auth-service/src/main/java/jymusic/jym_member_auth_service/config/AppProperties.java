package jymusic.jym_member_auth_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * 프론트엔드 Base URL. OAuth 콜백 성공 시 이 URL로 302 리다이렉트합니다.
     * 예: http://localhost:3000
     */
    private String frontBaseUrl = "http://localhost:3000";
}
