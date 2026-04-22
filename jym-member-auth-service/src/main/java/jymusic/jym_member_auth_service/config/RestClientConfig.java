package jymusic.jym_member_auth_service.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({OAuth2Properties.class, AppProperties.class})
public class RestClientConfig {

    @Bean
    public RestClient oauthRestClient() {
        return RestClient.builder().build();
    }
}
