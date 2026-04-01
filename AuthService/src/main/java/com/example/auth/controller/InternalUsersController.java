package com.example.auth.controller;

import com.example.auth.dto.response.InternalUserDto;
import com.example.auth.mapper.InternalUserMapper;
import com.example.auth.model.AuthUser;
import com.example.auth.model.Role;
import com.example.auth.repo.AuthUserRepository;
import com.example.auth.security.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/internal")
public class InternalUsersController {

    private final AuthUserRepository authUserRepository;
    private final TokenService tokenService;
    private final InternalUserMapper internalUserMapper;

    public InternalUsersController(
            AuthUserRepository authUserRepository,
            TokenService tokenService,
            InternalUserMapper internalUserMapper
    ) {
        this.authUserRepository = authUserRepository;
        this.tokenService = tokenService;
        this.internalUserMapper = internalUserMapper;
    }

    @GetMapping("/users")
    @Operation(summary = "List users by role (internal, admin JWT)")
    public List<InternalUserDto> users(
            @Parameter(
                    name = "Authorization",
                    in = ParameterIn.HEADER,
                    required = false,
                    description = "Bearer <accessToken> (Swagger Authorize or manual)."
            )
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Parameter(
                    name = "X-Authorization",
                    in = ParameterIn.HEADER,
                    required = false,
                    description = "Fallback manual token header if Authorization is not sent by the client."
            )
            @RequestHeader(name = "X-Authorization", required = false) String manualAuthorization,
            @RequestParam(name = "role", defaultValue = "ROLE_USER") Role role
    ) {
        String authHeader = (authorization == null || authorization.isBlank()) ? manualAuthorization : authorization;
        if (authHeader == null || authHeader.isBlank()) {
            throw new SecurityException("Missing Authorization header");
        }

        Object rolesClaim = tokenService.decodeClaims(authHeader).get("roles");
        List<String> roles = rolesClaim instanceof List<?> list
                ? list.stream().map(String::valueOf).collect(Collectors.toList())
                : List.of();

        if (roles.stream().noneMatch(r -> r.equals(Role.ROLE_ADMIN.name()))) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN required");
        }

        return authUserRepository.findAllByRole(role)
                .stream()
                .filter(AuthUser::isActive)
                .map(internalUserMapper::toDto)
                .collect(Collectors.toList());
    }
}

