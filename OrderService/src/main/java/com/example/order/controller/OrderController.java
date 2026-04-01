package com.example.order.controller;

import com.example.order.dto.OrderCreateRequest;
import com.example.order.dto.OrderCreateResponse;
import com.example.order.dto.OrderCartCreateRequest;
import com.example.order.dto.OrderCartCreateResponse;
import com.example.order.security.AuthContext;
import com.example.order.security.JwtAuthService;
import com.example.order.service.OrderPlacementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class OrderController {

    private final JwtAuthService jwtAuthService;
    private final OrderPlacementService orderPlacementService;
    private final String retailPromoId;

    public OrderController(
            JwtAuthService jwtAuthService,
            OrderPlacementService orderPlacementService,
            @Value("${app.order.retail-promo-id:RETAIL}") String retailPromoId
    ) {
        this.jwtAuthService = jwtAuthService;
        this.orderPlacementService = orderPlacementService;
        this.retailPromoId = retailPromoId;
    }

    @PostMapping("/orders")
    @Operation(
            summary = "Create order (user only)",
            description = "Một endpoint duy nhat. Bo qua field promoId (hoac de null) = mua le (kenh RETAIL). "
                    + "Gui promoId = BF2026 (vi du) = tham gia flash sale / kenh ton kho tuong ung."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<OrderCreateResponse> createOrder(
            @Valid @RequestBody OrderCreateRequest request,
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
        if (ctx.getRoles() == null || ctx.getRoles().stream().noneMatch(r -> r.equals("ROLE_USER"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String channel = resolvePromoChannel(request.getPromoId());
        return orderPlacementService.placeOrder(channel, ctx.getUserId(), request);
    }

    @PostMapping("/orders/cart")
    @Operation(
            summary = "Create cart order (user only)",
            description = "Một request có thể chứa nhiều sản phẩm. Dedup theo user + promoId + toàn bộ items."
    )
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<OrderCartCreateResponse> createCartOrder(
            @Valid @RequestBody OrderCartCreateRequest request,
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
        if (ctx.getRoles() == null || ctx.getRoles().stream().noneMatch(r -> r.equals("ROLE_USER"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String channel = resolvePromoChannel(request.getPromoId());
        return orderPlacementService.placeCartOrder(channel, ctx.getUserId(), request);
    }

    private String resolvePromoChannel(String bodyPromoId) {
        if (bodyPromoId == null || bodyPromoId.isBlank()) {
            return retailPromoId;
        }
        String p = bodyPromoId.trim();
        if (p.contains("{") || p.contains("}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "promoId phai la ma that (vi du BF2026), khong dung placeholder dang {promoId}. "
                            + "De trong promoId de mua le."
            );
        }
        return p;
    }
}
