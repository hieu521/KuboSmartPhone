package com.example.product.controller;

import com.example.product.dto.ProductCreateRequest;
import com.example.product.dto.ProductDto;
import com.example.product.model.Product;
import com.example.product.mapper.ProductDraftMapper;
import com.example.product.mapper.ProductMapper;
import com.example.product.repo.ProductRepository;
import com.example.product.security.AuthContext;
import com.example.product.security.JwtAuthService;
import com.example.common.pagination.PageBasedRequest;
import com.example.common.pagination.PageBasedResponse;
import com.example.common.pagination.PaginationMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping
public class ProductController {

    private final ProductRepository productRepository;
    private final JwtAuthService jwtAuthService;
    private final ProductMapper productMapper;
    private final ProductDraftMapper productDraftMapper;

    public ProductController(
            ProductRepository productRepository,
            JwtAuthService jwtAuthService,
            ProductMapper productMapper,
            ProductDraftMapper productDraftMapper
    ) {
        this.productRepository = productRepository;
        this.jwtAuthService = jwtAuthService;
        this.productMapper = productMapper;
        this.productDraftMapper = productDraftMapper;
    }

    @PostMapping("/admin/products")
    @Operation(summary = "Create product (admin only)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ProductDto> create(
            @Valid @RequestBody ProductCreateRequest request,
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ROLE_ADMIN is required");
        }

        Product p = productDraftMapper.toNewEntity(request);

        Product saved = productRepository.save(p);
        return ResponseEntity.status(HttpStatus.CREATED).body(productMapper.toDto(saved));
    }

    @GetMapping("/products/{id}")
    public ProductDto getById(@PathVariable Long id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        return productMapper.toDto(p);
    }

    @GetMapping("/products")
    public PageBasedResponse<ProductDto> list(
            @Valid PageBasedRequest pageRequest
    ) {
        Pageable pageable = pageRequest.toPageable();
        Page<Product> page = productRepository.findAll(pageable);
        return PaginationMapper.toResponse(page, productMapper::toDto);
    }
}

