package com.example.inventory.controller;

import com.example.inventory.dto.PromoStockResponse;
import com.example.inventory.dto.StockUpdateRequest;
import com.example.inventory.mapping.PromoStockMappingHelper;
import com.example.inventory.mapper.PromoStockMapper;
import com.example.inventory.model.PromoStock;
import com.example.inventory.repo.PromoStockRepository;
import com.example.inventory.security.AuthContext;
import com.example.inventory.security.JwtAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/promo-stocks")
public class InventoryAdminController {

    private final PromoStockRepository promoStockRepository;
    private final StringRedisTemplate redisTemplate;
    private final JwtAuthService jwtAuthService;
    private final PromoStockMapper promoStockMapper;

    public InventoryAdminController(
            PromoStockRepository promoStockRepository,
            StringRedisTemplate redisTemplate,
            JwtAuthService jwtAuthService,
            PromoStockMapper promoStockMapper
    ) {
        this.promoStockRepository = promoStockRepository;
        this.redisTemplate = redisTemplate;
        this.jwtAuthService = jwtAuthService;
        this.promoStockMapper = promoStockMapper;
    }

    @GetMapping("/{promoId}/products/{productId}/stock")
    @Operation(summary = "Get promo stock (admin only)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PromoStockResponse> getPromoStock(
            @PathVariable String promoId,
            @PathVariable Long productId,
            @Parameter(
                    name = "Authorization",
                    in = ParameterIn.HEADER,
                    required = false,
                    description = "Used by Swagger Authorize button."
            )
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Parameter(
                    name = "X-Authorization",
                    in = ParameterIn.HEADER,
                    required = false,
                    description = "Manual token header. Use Bearer <token> or plain token."
            )
            @RequestHeader(name = "X-Authorization", required = false) String manualAuthorization
    ) {
        String authHeader = (authorization == null || authorization.isBlank()) ? manualAuthorization : authorization;
        AuthContext ctx = jwtAuthService.authenticate(authHeader);
        if (ctx.getRoles() == null || ctx.getRoles().stream().noneMatch(r -> r.equals("ROLE_ADMIN"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return promoStockRepository.findByPromoIdAndProductId(promoId, productId)
                .map(promoStockMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{promoId}/products/{productId}/stock")
    @Operation(summary = "Set promo stock (admin only)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PromoStockResponse> setPromoStock(
            @PathVariable String promoId,
            @PathVariable Long productId,
            @RequestBody StockUpdateRequest request,
            @Parameter(
                    name = "Authorization",
                    in = ParameterIn.HEADER,
                    required = false,
                    description = "Used by Swagger Authorize button."
            )
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Parameter(
                    name = "X-Authorization",
                    in = ParameterIn.HEADER,
                    required = false,
                    description = "Manual token header. Use Bearer <token> or plain token."
            )
            @RequestHeader(name = "X-Authorization", required = false) String manualAuthorization
    ) {
        String authHeader = (authorization == null || authorization.isBlank()) ? manualAuthorization : authorization;
        AuthContext ctx = jwtAuthService.authenticate(authHeader);
        if (ctx.getRoles() == null || ctx.getRoles().stream().noneMatch(r -> r.equals("ROLE_ADMIN"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        long stock = request.getStock();

        PromoStock entity = promoStockRepository.findByPromoIdAndProductId(promoId, productId)
                .orElseGet(() -> {
                    PromoStock ps = new PromoStock();
                    ps.setPromoId(promoId);
                    ps.setProductId(productId);
                    return ps;
                });
        entity.setStock(stock);
        PromoStock saved = promoStockRepository.save(entity);

        String stockKey = PromoStockMappingHelper.redisStockKey(promoId, productId);
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
        return ResponseEntity.ok(promoStockMapper.toResponse(saved));
    }
}

