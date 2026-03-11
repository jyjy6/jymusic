package jymusic.jym_api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * API Gateway 라우팅 설정.
 *
 * JwtVerificationFilter가 이미 X-User-Id / X-User-Name / X-User-Role 헤더를
 * HttpServletRequestWrapper를 통해 요청에 주입하므로,
 * 각 라우트는 단순히 목적지 URI를 지정하는 것만으로 사용자 컨텍스트를 전달할 수 있습니다.
 */
@Configuration
public class GatewayRoutingConfig {

    @Value("${services.member-auth.url}")
    private String memberAuthUrl;

    @Value("${services.catalog.url}")
    private String catalogUrl;

    @Value("${services.order.url}")
    private String orderUrl;

    @Value("${services.payment.url}")
    private String paymentUrl;

    @Bean
    public RouterFunction<ServerResponse> memberAuthRoute() {
        return route("member_auth_service")
                .route(path("/api/v1/auth/**"), http())
                .route(path("/api/v1/members/**"), http())
                .before(uri(memberAuthUrl))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> catalogRoute() {
        return route("catalog_service")
                .route(path("/api/v1/products/**"), http())
                .route(path("/api/v1/categories/**"), http())
                .route(path("/api/v1/media/**"), http())
                .before(uri(catalogUrl))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> orderRoute() {
        return route("order_service")
                .route(path("/api/v1/orders/**"), http())
                .before(uri(orderUrl))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> paymentRoute() {
        return route("payment_service")
                .route(path("/api/v1/payments/**"), http())
                .before(uri(paymentUrl))
                .build();
    }
}
