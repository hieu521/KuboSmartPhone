package com.example.product.config;

import com.example.product.model.Product;
import com.example.product.repo.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class ProductSeedConfig {

    @Bean
    CommandLineRunner productSeedRunner(
            ProductRepository productRepository,
            @Value("${app.seed.enabled:true}") boolean seedEnabled
    ) {
        return args -> {
            if (!seedEnabled || productRepository.count() > 0) {
                return;
            }

            productRepository.save(create("iPhone 15 128GB", new BigDecimal("20990000")));
            productRepository.save(create("Samsung S24 256GB", new BigDecimal("18990000")));
            productRepository.save(create("Xiaomi 14 256GB", new BigDecimal("13990000")));
        };
    }

    private static Product create(String name, BigDecimal price) {
        Product p = new Product();
        p.setName(name);
        p.setBasePrice(price);
        p.setActive(true);
        return p;
    }
}
