package jymusic.jym_api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.web.servlet.function.RequestPredicates.path;

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
                .filter((request, next) -> {
                    ServerRequest modifiedRequest = ServerRequest.from(request)
                            .header("X-User-Id", String.valueOf(request.attribute("X-User-Id").orElse("")))
                            .header("X-User-Name", String.valueOf(request.attribute("X-User-Name").orElse("")))
                            .header("X-User-Role", String.valueOf(request.attribute("X-User-Role").orElse("")))
                            .build();
                    return next.handle(modifiedRequest);
                })
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> catalogRoute() {
        return route("catalog_service")
                .route(path("/api/v1/products/**"), http())
                .route(path("/api/v1/categories/**"), http())
                .before(uri(catalogUrl))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> orderRoute() {
        return route("order_service")
                .route(path("/api/v1/orders/**"), http())
                .before(uri(orderUrl))
                .filter((request, next) -> {
                    ServerRequest modifiedRequest = ServerRequest.from(request)
                            .header("X-User-Id", String.valueOf(request.attribute("X-User-Id").orElse("")))
                            .header("X-User-Name", String.valueOf(request.attribute("X-User-Name").orElse("")))
                            .header("X-User-Role", String.valueOf(request.attribute("X-User-Role").orElse("")))
                            .build();
                    return next.handle(modifiedRequest);
                })
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> paymentRoute() {
        return route("payment_service")
                .route(path("/api/v1/payments/**"), http())
                .before(uri(paymentUrl))
                .filter((request, next) -> {
                    ServerRequest modifiedRequest = ServerRequest.from(request)
                            .header("X-User-Id", String.valueOf(request.attribute("X-User-Id").orElse("")))
                            .header("X-User-Name", String.valueOf(request.attribute("X-User-Name").orElse("")))
                            .header("X-User-Role", String.valueOf(request.attribute("X-User-Role").orElse("")))
                            .build();
                    return next.handle(modifiedRequest);
                })
                .build();
    }
}
