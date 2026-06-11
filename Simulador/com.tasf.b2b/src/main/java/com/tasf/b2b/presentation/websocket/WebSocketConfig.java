package com.tasf.b2b.presentation.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SimulationWebSocketHandler handler;
    private final JwtHandshakeInterceptor    interceptor;
    private final String[]                   allowedOrigins;

    public WebSocketConfig(SimulationWebSocketHandler handler,
                           JwtHandshakeInterceptor interceptor,
                           @Value("${app.cors.allowed-origins}") String allowedOriginsConfig) {
        this.handler        = handler;
        this.interceptor    = interceptor;
        this.allowedOrigins = allowedOriginsConfig.split(",");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/api/v1/simulations/{id}/ws")
                .addInterceptors(interceptor)
                .setAllowedOrigins(allowedOrigins);
    }
}
