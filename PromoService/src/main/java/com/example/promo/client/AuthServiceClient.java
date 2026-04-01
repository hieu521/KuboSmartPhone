package com.example.promo.client;

import com.example.promo.dto.AuthLoginRequest;
import com.example.promo.dto.AuthTokensResponse;
import com.example.promo.dto.InternalUserDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
public class AuthServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.auth-service.base-url}")
    private String baseUrl;

    public AuthServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String loginAndGetAccessToken(String email, String password) {
        AuthLoginRequest request = new AuthLoginRequest();
        request.setEmail(email);
        request.setPassword(password);

        ResponseEntity<AuthTokensResponse> response = restTemplate.postForEntity(
                baseUrl + "/auth/login",
                request,
                AuthTokensResponse.class
        );
        return response.getBody().getAccessToken();
    }

    public List<InternalUserDto> getUsersForCampaign(String accessToken, String role) {
        String url = baseUrl + "/internal/users?role=" + role;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<List<InternalUserDto>> response = restTemplate.exchange(
                url,
                org.springframework.http.HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<List<InternalUserDto>>() {}
        );

        return response.getBody();
    }
}

