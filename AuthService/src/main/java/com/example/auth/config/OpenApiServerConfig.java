package com.example.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Mac dinh springdoc lay server tu request toi auth-service:8083 -> Swagger UI trong browser
 * goi http://auth-service:8083/... (DNS cluster, khong ton tai tren may ban) => "Failed to fetch".
 * Dat server tuong doi "/" de Try-it-out giai quyet theo host hien tai (Gateway qua Ingress/port-forward).
 * Tuy chon: app.openapi.servers=https://api.cua-ban.com (nhieu URL cach nhau dau phay).
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
                    .description("Relative — same host as Swagger (must use API Gateway /auth/... in browser)"));
        }
        return new OpenAPI().servers(servers);
    }
}
