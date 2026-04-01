package com.example.order.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.amqp.AmqpException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR = "error";
    private static final String MESSAGE = "message";

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<Map<String, Object>> onRedisFailure(RedisConnectionFailureException e) {
        log.warn("Redis connection failed", e);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ERROR, "Service Unavailable");
        body.put(MESSAGE, "Redis khong ket noi. Chay Docker: docker compose up -d redis. " + e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /**
     * Redis Lua/script errors are wrapped as {@link RedisSystemException}, which extends
     * {@link DataAccessException} — must be handled before generic DB handler or users see false "MySQL" errors.
     */
    @ExceptionHandler(RedisSystemException.class)
    public ResponseEntity<Map<String, Object>> onRedisSystem(RedisSystemException e) {
        log.warn("Redis script/command error (often Lua EVAL on order draft)", e);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ERROR, "Redis Error");
        body.put(MESSAGE,
                "Loi Redis/Lua khi dat hang (EVAL script, kieu key sai, hoac Redis tu choi lenh). "
                        + "Kiem tra: docker compose up -d redis; chay InventoryService de seed promo:stock:* (string). "
                        + "Chi tiet: " + dataAccessDetail(e));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> onDataAccess(DataAccessException e) {
        log.warn("Database error", e);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ERROR, "Database Error");
        body.put(MESSAGE,
                "Loi truy van MySQL (JPA). Kiem tra Docker mysql-order (port 3312), user orders/orders123. "
                        + "Chi tiet: " + dataAccessDetail(e));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private static String dataAccessDetail(DataAccessException e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        Throwable root = e.getRootCause();
        if (root != null && !Objects.equals(root.getMessage(), e.getMessage())) {
            String r = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
            msg = msg + " | root: " + root.getClass().getSimpleName() + ": " + r;
        }
        return msg;
    }

    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<Map<String, Object>> onAmqp(AmqpException e) {
        log.warn("RabbitMQ error", e);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ERROR, "Service Unavailable");
        body.put(MESSAGE, "RabbitMQ khong ket noi. Chay Docker: docker compose up -d rabbitmq. " + e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, Object>> onResourceAccess(ResourceAccessException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ERROR, "Bad Gateway");
        body.put(MESSAGE, "Khong ket noi duoc ProductService. Kiem tra da chay ProductService (port 8086) va "
                + "bien PRODUCT_SERVICE_BASE_URL / app.product-service.base-url. Chi tiet: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<Map<String, Object>> onHttpClient(HttpClientErrorException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ERROR, "Upstream Client Error");
        body.put(MESSAGE,
                "ProductService tra loi " + e.getStatusCode().value()
                        + ". Kiem tra productId co ton tai (GET /products/{id}). "
                        + e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<Map<String, Object>> onHttpServer(HttpServerErrorException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ERROR, "Upstream Server Error");
        body.put(MESSAGE, e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, Object>> onRestClient(RestClientException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ERROR, "Bad Gateway");
        body.put(MESSAGE, "Loi goi ProductService: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> onIllegalState(IllegalStateException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ERROR, "Bad Gateway");
        body.put(MESSAGE, e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> onAny(Exception e) {
        log.error("Unhandled exception for /orders", e);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ERROR, "Internal Server Error");
        body.put(MESSAGE,
                "Loi ben trong. Kiem tra: Docker (redis, rabbitmq, mysql-order), ProductService (8086), InventoryService (8087) da chay? "
                        + "Chi tiet: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
