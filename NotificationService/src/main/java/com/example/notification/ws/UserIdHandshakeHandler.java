package com.example.notification.ws;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

public class UserIdHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    @Nullable
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        if (request instanceof org.springframework.http.server.ServletServerHttpRequest servletRequest) {
            HttpServletRequest http = servletRequest.getServletRequest();
            String userId = http.getParameter("userId");
            if (userId != null && !userId.isBlank()) {
                String finalUserId = userId.trim();
                return () -> finalUserId;
            }
        }
        return super.determineUser(request, wsHandler, attributes);
    }
}

