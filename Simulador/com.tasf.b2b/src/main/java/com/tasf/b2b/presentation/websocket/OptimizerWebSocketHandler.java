package com.tasf.b2b.presentation.websocket;

import com.tasf.b2b.application.usecase.SimulationRegistry;
import com.tasf.b2b.application.usecase.SimulationSession;
import com.tasf.b2b.domain.optimizer.metrics.OptimizerPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Handler WebSocket para métricas del optimizador.
 * Endpoint: /api/v1/simulations/{id}/ws/optimizer
 * Publica: ALGORITHM_RUN (por ejecución) y COLLAPSE_DETAIL (al colapsar).
 */
@Component
public class OptimizerWebSocketHandler extends TextWebSocketHandler {

    private final SimulationRegistry registry;

    public OptimizerWebSocketHandler(SimulationRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        InMemoryOptimizerPublisher publisher = resolvePublisher(session);
        if (publisher == null) { closeQuietly(session, CloseStatus.BAD_DATA); return; }
        publisher.subscribe(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        InMemoryOptimizerPublisher publisher = resolvePublisher(session);
        if (publisher != null) publisher.unsubscribe(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        InMemoryOptimizerPublisher publisher = resolvePublisher(session);
        if (publisher != null) publisher.unsubscribe(session);
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    private InMemoryOptimizerPublisher resolvePublisher(WebSocketSession session) {
        String sessionId = extractSessionId(session);
        if (sessionId == null) {
            System.err.println("[OptWS] No se pudo extraer sessionId de URI: " + session.getUri());
            return null;
        }
        try {
            SimulationSession simSession = registry.findOrThrow(sessionId);
            OptimizerPublisher publisher = simSession.getOptimizerPublisher();
            if (publisher instanceof InMemoryOptimizerPublisher p) return p;
            System.err.println("[OptWS] publisher no es InMemoryOptimizerPublisher: " + publisher);
        } catch (Exception e) {
            System.err.println("[OptWS] Error resolviendo sesión '" + sessionId + "': " + e.getMessage());
        }
        return null;
    }

    private String extractSessionId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : null;
        if (path == null) return null;
        // /api/v1/simulations/{id}/ws/optimizer
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("simulations".equals(parts[i]) && i + 1 < parts.length) return parts[i + 1];
        }
        return null;
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try { session.close(status); } catch (Exception ignored) {}
    }
}
