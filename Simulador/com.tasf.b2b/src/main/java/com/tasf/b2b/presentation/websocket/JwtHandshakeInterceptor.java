package com.tasf.b2b.presentation.websocket;

import com.tasf.b2b.infrastructure.security.JwtService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Valida el JWT antes de aceptar la conexión WebSocket.
 *
 * El token se envía como query param: ws://host/api/v1/simulations/{id}/ws?token=<jwt>
 * Los navegadores no pueden añadir cabeceras personalizadas en el handshake WebSocket,
 * por eso usamos query param en lugar de Authorization header.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    public JwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        if (query == null) return false;

        String token = null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                token = param.substring(6);
                break;
            }
        }
        if (token == null) return false;

        try {
            String username = jwtService.extractUsername(token);
            if (username == null || !jwtService.isValid(token, username)) return false;
            attributes.put("username", username);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {}
}
