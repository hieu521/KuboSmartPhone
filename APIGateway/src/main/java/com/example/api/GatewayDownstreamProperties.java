package com.example.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * URL backend cho từng microservice.
 * <p>
 * Chạy local: mặc định {@code localhost} (cổng giống {@code application.yml}).
 * Chạy Kubernetes: set biến môi trường {@code APP_GATEWAY_DOWNSTREAM_*}
 * (Spring Boot map sang {@code app.gateway.downstream.*}) trỏ tới Service DNS,
 * ví dụ {@code http://order-service:8088}.
 * </p>
 */
@ConfigurationProperties(prefix = "app.gateway.downstream")
public class GatewayDownstreamProperties {

    /**
     * Ví dụ K8s: http://user-service:8081
     */
    private String userService = "http://localhost:8081";

    /**
     * Auth service (JWT, login, refresh).
     * Ví dụ K8s: http://auth-service:8083
     */
    private String authService = "http://localhost:8083";

    private String departmentService = "http://localhost:8082";

    private String orderService = "http://localhost:8088";

    private String promoService = "http://localhost:8085";

    private String inventoryService = "http://localhost:8087";

    public String getUserService() {
        return userService;
    }

    public void setUserService(String userService) {
        this.userService = userService;
    }

    public String getDepartmentService() {
        return departmentService;
    }

    public void setDepartmentService(String departmentService) {
        this.departmentService = departmentService;
    }

    public String getOrderService() {
        return orderService;
    }

    public void setOrderService(String orderService) {
        this.orderService = orderService;
    }

    public String getPromoService() {
        return promoService;
    }

    public void setPromoService(String promoService) {
        this.promoService = promoService;
    }

    public String getInventoryService() {
        return inventoryService;
    }

    public void setInventoryService(String inventoryService) {
        this.inventoryService = inventoryService;
    }

    public String getAuthService() {
        return authService;
    }

    public void setAuthService(String authService) {
        this.authService = authService;
    }
}
