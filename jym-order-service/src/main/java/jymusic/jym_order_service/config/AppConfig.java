package jymusic.jym_order_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    @Bean
    public RestClient catalogRestClient(@Value("${services.catalog.url}") String url) {
        return RestClient.builder().baseUrl(url).build();
    }
}
