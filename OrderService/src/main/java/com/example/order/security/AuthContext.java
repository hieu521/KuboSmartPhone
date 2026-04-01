package com.example.order.security;

import java.util.List;

public class AuthContext {
    private final Long userId;
    private final List<String> roles;

    public AuthContext(Long userId, List<String> roles) {
        this.userId = userId;
        this.roles = roles;
    }

    public Long getUserId() {
        return userId;
    }

    public List<String> getRoles() {
        return roles;
    }
}

