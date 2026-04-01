package com.example.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions.rateLimit;
// Eureka + LoadBalancer — giữ import comment để tham chiếu khi cần bật lại discovery:
// import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

@Configuration
public class RateLimitedRoutesConfig {

    private static final String ANONYMOUS = "anonymous";

    private final GatewayDownstreamProperties downstream;

    public RateLimitedRoutesConfig(GatewayDownstreamProperties downstream) {
        this.downstream = downstream;
    }

    @Bean
    public RouterFunction<ServerResponse> userServiceRouteRateLimited() {
        return route("user-service-rate-limited")
                .GET("/users/**", http())
                .POST("/users/**", http())
                .filter(rateLimit(c -> c
                        .setCapacity(50)
                        .setPeriod(Duration.ofMinutes(1))
                        .setKeyResolver(request -> {
                            String ip = request.servletRequest().getRemoteAddr();
                            return ip != null ? ip : ANONYMOUS;
                        })))
                // Kubernetes / DNS nội bộ: URI cố định tới Service (thay cho lb("user-service") + Eureka)
                .before(uri(downstream.getUserService()))
                // .filter(lb("user-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> departmentServiceRouteRateLimited() {
        return route("department-service-rate-limited")
                .GET("/departments/**", http())
                .POST("/departments/**", http())
                .filter(rateLimit(c -> c
                        .setCapacity(50)
                        .setPeriod(Duration.ofMinutes(1))
                        .setKeyResolver(request -> {
                            String ip = request.servletRequest().getRemoteAddr();
                            return ip != null ? ip : ANONYMOUS;
                        })))
                .before(uri(downstream.getDepartmentService()))
                // .filter(lb("department-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> orderServiceRouteRateLimited() {
        return route("order-service-rate-limited")
                .GET("/orders/**", http())
                .POST("/orders/**", http())
                .filter(rateLimit(c -> c
                        .setCapacity(50)
                        .setPeriod(Duration.ofMinutes(1))
                        .setKeyResolver(request -> {
                            String ip = request.servletRequest().getRemoteAddr();
                            return ip != null ? ip : ANONYMOUS;
                        })))
                .before(uri(downstream.getOrderService()))
                // .filter(lb("order-service"))
                .build();
    }

    /**
     * Order Swagger UI duoi prefix /orders (khong dung /swagger-ui root vi root dang phuc vu Auth swagger).
     *
     * Entry point:
     * - http://api.javaspring.local/orders/swagger-ui/index.html
     *
     * OpenAPI:
     * - http://api.javaspring.local/orders/v3/api-docs
     */
    @Bean
    @Order(-26)
    public RouterFunction<ServerResponse> orderSwaggerRedirectRoute() {
        return route("order-swagger-redirect")
                .GET("/orders/swagger-ui.html", req ->
                        ServerResponse.temporaryRedirect(URI.create("/orders/swagger-ui/index.html")).build())
                .build();
    }

    @Bean
    @Order(-25)
    public RouterFunction<ServerResponse> orderSwaggerProxyRoute() {
        return route("order-swagger-proxy")
                // Map /orders/swagger-ui/** -> backend /swagger-ui/**
                .GET("/orders/swagger-ui/**", http())
                .HEAD("/orders/swagger-ui/**", http())
                // Map /orders/v3/api-docs/** -> backend /v3/api-docs/**
                .GET("/orders/v3/api-docs/**", http())
                .HEAD("/orders/v3/api-docs/**", http())
                .before(stripPrefix(1))
                .before(uri(downstream.getOrderService()))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> promoServiceRouteRateLimited() {
        return route("promo-service-rate-limited")
                .POST("/admin/campaigns/**", http())
                .filter(rateLimit(c -> c
                        .setCapacity(50)
                        .setPeriod(Duration.ofMinutes(1))
                        .setKeyResolver(request -> {
                            String ip = request.servletRequest().getRemoteAddr();
                            return ip != null ? ip : ANONYMOUS;
                        })))
                .before(uri(downstream.getPromoService()))
                // .filter(lb("promo-service"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> inventoryServiceRouteRateLimited() {
        return route("inventory-service-rate-limited")
                .POST("/admin/promo-stocks/**", http())
                .filter(rateLimit(c -> c
                        .setCapacity(50)
                        .setPeriod(Duration.ofMinutes(1))
                        .setKeyResolver(request -> {
                            String ip = request.servletRequest().getRemoteAddr();
                            return ip != null ? ip : ANONYMOUS;
                        })))
                .before(uri(downstream.getInventoryService()))
                // .filter(lb("inventory-service"))
                .build();
    }

    /**
     * Auth-service qua Gateway (không rate limit).
     * Cho phép truy cập swagger, login, refresh... qua domain Ingress.
     *
     * Ví dụ:
     * - http://api.javaspring.local/auth/swagger-ui/index.html
     * - http://api.javaspring.local/auth/v3/api-docs
     */
    @Bean
    @Order(-30)
    public RouterFunction<ServerResponse> authSwaggerPrefixedRoute() {
        return route("auth-swagger-prefixed")
                // AuthService springdoc is at root: /swagger-ui.html, /swagger-ui/**, /v3/api-docs/**
                // When exposing under /auth/*, strip prefix only for swagger/openapi paths.
                .GET("/auth/swagger-ui.html", http())
                .GET("/auth/swagger-ui/**", http())
                .GET("/auth/v3/api-docs/**", http())
                .before(stripPrefix(1))
                .before(uri(downstream.getAuthService()))
                .build();
    }

    @Bean
    @Order(0)
    public RouterFunction<ServerResponse> authServiceRoute() {
        return route("auth-service")
                .GET("/auth/**", http())
                .POST("/auth/**", http())
                .before(uri(downstream.getAuthService()))
                .build();
    }

    /**
     * Swagger UI (auth-service) calls these absolute URLs by default:
     * - /v3/api-docs
     * - /v3/api-docs/swagger-config
     * When accessing auth swagger through gateway, proxy them to auth-service to avoid clashes.
     */
    @Bean
    @Order(-20)
    public RouterFunction<ServerResponse> authOpenApiPassthroughRoute() {
        return route("auth-openapi")
                .GET("/v3/api-docs/**", http())
                .before(uri(downstream.getAuthService()))
                .build();
    }

    /**
     * Auth Swagger UI redirect tu /auth/swagger-ui.html -> Location: /swagger-ui/index.html (root),
     * khong giu prefix /auth. Proxy /swagger-ui/** ve auth-service de trinh duyet theo redirect van vao dung backend.
     */
    @Bean
    @Order(-18)
    public RouterFunction<ServerResponse> authSwaggerUiAssetsFromRootRoute() {
        return route("auth-swagger-ui-root")
                .GET("/swagger-ui/**", http())
                .GET("/swagger-ui.html", http())
                .before(uri(downstream.getAuthService()))
                .build();
    }

    /**
     * Swagger UI dưới prefix /auth cần swagger-config trỏ đúng OpenAPI URL.
     * Nếu proxy nguyên bản, swagger-config sẽ trỏ về "/v3/api-docs" (gateway) -> "No operations defined in spec!".
     */
    @Bean
    @Order(-10)
    public RouterFunction<ServerResponse> authSwaggerConfigRoute() {
        return route("auth-swagger-config")
                .GET("/auth/v3/api-docs/swagger-config", request ->
                        ServerResponse.ok().body(Map.of(
                                "configUrl", "/auth/v3/api-docs/swagger-config",
                                "oauth2RedirectUrl", "http://api.javaspring.local/auth/swagger-ui/oauth2-redirect.html",
                                "url", "/auth/v3/api-docs",
                                "validatorUrl", ""
                        )))
                .build();
    }

    /**
     * "/" khong co route proxy nao -> Tomcat 404. Chuyen huong toi Swagger cua Gateway.
     */
    @Bean
    @Order(100)
    public RouterFunction<ServerResponse> gatewayRootRedirectRoute() {
        return route("gateway-root-redirect")
                .GET("/", request -> ServerResponse.temporaryRedirect(URI.create("/gateway/swagger-ui.html")).build())
                .build();
    }
}
