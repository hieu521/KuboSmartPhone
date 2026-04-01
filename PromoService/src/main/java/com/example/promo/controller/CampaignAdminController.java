package com.example.promo.controller;

import com.example.promo.dto.TriggerResponse;
import com.example.promo.security.AuthContext;
import com.example.promo.security.JwtAuthService;
import com.example.promo.service.CampaignTriggerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/campaigns")
public class CampaignAdminController {

    private final CampaignTriggerService campaignTriggerService;
    private final JwtAuthService jwtAuthService;

    public CampaignAdminController(CampaignTriggerService campaignTriggerService, JwtAuthService jwtAuthService) {
        this.campaignTriggerService = campaignTriggerService;
        this.jwtAuthService = jwtAuthService;
    }

    @PostMapping("/blackfriday/{campaignId}/trigger")
    @Operation(summary = "Trigger Black Friday campaign (admin only)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<TriggerResponse> triggerBlackFriday(
            @PathVariable("campaignId") @NotBlank String campaignId,
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
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(campaignTriggerService.triggerBlackFriday(campaignId));
    }
}

