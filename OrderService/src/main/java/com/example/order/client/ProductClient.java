package com.example.order.client;

import com.example.order.client.dto.ProductDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
public class ProductClient {

    private final RestTemplate restTemplate;

    @Value("${app.product-service.base-url}")
    private String baseUrl;

    public ProductClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public BigDecimal getBasePrice(Long productId) {
        String url = baseUrl + "/products/" + productId;
        ResponseEntity<ProductDto> resp = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(new HttpHeaders()),
                ProductDto.class
        );
        ProductDto body = resp.getBody();
        if (body == null || body.getBasePrice() == null) {
            throw new IllegalStateException("Product price not found for productId=" + productId);
        }
        return body.getBasePrice();
    }
}

