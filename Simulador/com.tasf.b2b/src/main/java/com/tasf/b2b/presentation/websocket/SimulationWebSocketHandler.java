package com.tasf.b2b.presentation.websocket;

import com.tasf.b2b.application.usecase.SimulationRegistry;
import com.tasf.b2b.application.usecase.SimulationSession;
import com.tasf.b2b.domain.simulator.StatePublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Handler WebSocket para streaming de eventos de simulación.
 *
 * Al conectarse, el cliente se registra en el InMemoryStatePublisher de su sesión.
 * El hilo drenador del publisher le enviará los eventos a medida que ocurran.
 * Al desconectarse, se desregistra automáticamente.
 *
 * Auth: validada antes de llegar aquí por JwtHandshakeInterceptor.
 */
@Component
public class SimulationWebSocketHandler extends TextWebSocketHandler {

    private final SimulationRegistry registry;

    public SimulationWebSocketHandler(SimulationRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        InMemoryStatePublisher publisher = resolvePublisher(session);
        if (publisher == null) {
            closeQuietly(session, CloseStatus.BAD_DATA);
            return;
        }
        publisher.subscribe(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        InMemoryStatePublisher publisher = resolvePublisher(session);
        if (publisher != null) publisher.unsubscribe(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        InMemoryStatePublisher publisher = resolvePublisher(session);
        if (publisher != null) publisher.unsubscribe(session);
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private InMemoryStatePublisher resolvePublisher(WebSocketSession session) {
        String sessionId = extractSessionId(session);
        if (sessionId == null) return null;
        try {
            SimulationSession simSession = registry.findOrThrow(sessionId);
            StatePublisher publisher = simSession.getPublisher();
            if (publisher instanceof InMemoryStatePublisher p) return p;
        } catch (Exception ignored) {}
        return null;
    }

    private String extractSessionId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : null;
        if (path == null) return null;
        // /api/v1/simulations/{id}/ws
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("simulations".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return null;
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try { session.close(status); } catch (Exception ignored) {}
    }
}
