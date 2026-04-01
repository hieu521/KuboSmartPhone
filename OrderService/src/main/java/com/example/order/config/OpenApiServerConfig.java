package com.example.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Swagger UI trong browser khong the goi DNS noi bo K8s (vd http://order-service:8088).
 * Dat server tuong doi "/" de Try-it-out goi theo host hien tai (API Gateway / Ingress / port-forward).
 *
 * Tuy chon: app.openapi.servers=http://api.javaspring.local (nhieu URL cach nhau dau phay).
 */
@Configuration
public class OpenApiServerConfig {

    @Bean
    public OpenAPI openAPI(@Value("${app.openapi.servers:}") String serversCsv) {
        List<Server> servers = new ArrayList<>();
        if (serversCsv != null && !serversCsv.isBlank()) {
            for (String part : serversCsv.split(",")) {
                String url = part.trim();
                if (!url.isEmpty()) {
                    servers.add(new Server().url(url).description("app.openapi.servers"));
                }
            }
        }
        if (servers.isEmpty()) {
            servers.add(new Server()
                    .url("/")
                    .description("Relative — same host as Swagger (use API Gateway /orders/... in browser)"));
        }
        return new OpenAPI().servers(servers);
    }
}

