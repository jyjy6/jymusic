package jymusic.jym_api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRouteConfig {

    @Value("${services.member-auth.url}")
    private String memberAuthUrl;

    @Value("${services.catalog.url}")
    private String catalogUrl;

    @Value("${services.order.url}")
    private String orderUrl;

    @Value("${services.payment.url}")
    private String paymentUrl;

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("member_auth_service", r -> r
                        .path("/api/v1/auth/**", "/api/v1/members/**")
                        .uri(memberAuthUrl))
                .route("catalog_service", r -> r
                        .path("/api/v1/products/**", "/api/v1/categories/**", "/api/v1/media/**")
                        .uri(catalogUrl))
                .route("order_service", r -> r
                        .path("/api/v1/cart/**", "/api/v1/orders/**")
                        .uri(orderUrl))
                .route("payment_service", r -> r
                        .path("/api/v1/payments/**")
                        .uri(paymentUrl))
                .build();
    }
}
