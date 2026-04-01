package com.example.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration
public class JwtConfig {

    @Bean
    public SecretKey jwtHmacSecretKey(@Value("${auth.jwt.secret}") String base64Secret) {
        byte[] decoded = Base64.getDecoder().decode(base64Secret);
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtHmacSecretKey) {
        byte[] secretBytes = jwtHmacSecretKey.getEncoded();
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(secretBytes)
                .algorithm(JWSAlgorithm.HS256)
                .build();
        JWKSet jwkSet = new JWKSet(jwk);
        ImmutableJWKSet<SecurityContext> jwkSource = new ImmutableJWKSet<>(jwkSet);
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtHmacSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtHmacSecretKey).build();
    }
}

