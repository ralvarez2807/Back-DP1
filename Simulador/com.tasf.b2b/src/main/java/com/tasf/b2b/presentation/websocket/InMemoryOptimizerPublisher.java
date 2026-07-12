package com.tasf.b2b.presentation.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tasf.b2b.domain.optimizer.metrics.AlgorithmRunDTO;
import com.tasf.b2b.domain.optimizer.metrics.CollapseDetailDTO;
import com.tasf.b2b.domain.optimizer.metrics.OptimizerEventDTO;
import com.tasf.b2b.domain.optimizer.metrics.OptimizerPublisher;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Canal WebSocket exclusivo para métricas del optimizador.
 * Mismo patrón que InMemoryStatePublisher: cola + hilo drenador daemon.
 * Endpoint: /api/v1/simulations/{id}/ws/optimizer
 */
public class InMemoryOptimizerPublisher implements OptimizerPublisher {

    private final BlockingQueue<OptimizerEventDTO>       queue       = new LinkedBlockingQueue<>();
    private final CopyOnWriteArrayList<WebSocketSession> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicLong                             seq         = new AtomicLong(0);
    private final Thread                                 drainThread;
    private final ObjectMapper                           mapper;
    private volatile boolean                             closing     = false;

    public InMemoryOptimizerPublisher(String sessionId) {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.drainThread = new Thread(this::drain, "ws-optimizer-drain-" + sessionId);
        this.drainThread.setDaemon(true);
        this.drainThread.start();
    }

    @Override
    public void publish(OptimizerEventDTO event) {
        queue.offer(event);
    }

    /** Mismo cierre prolijo que InMemoryStatePublisher: flush de la cola, luego cierra los sockets. */
    @Override
    public void close() {
        closing = true;
        drainThread.interrupt();
    }

    public void subscribe(WebSocketSession session) {
        subscribers.add(session);
    }

    public void unsubscribe(WebSocketSession session) {
        subscribers.remove(session);
    }

    private void drain() {
        while (!closing) {
            try {
                broadcast(queue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        OptimizerEventDTO dto;
        while ((dto = queue.poll()) != null) {
            broadcast(dto);
        }
        for (WebSocketSession session : subscribers) {
            try { session.close(CloseStatus.NORMAL); } catch (Exception ignored) {}
        }
        subscribers.clear();
    }

    private void broadcast(OptimizerEventDTO dto) {
        try {
            String json = buildEnvelope(dto);
            TextMessage msg = new TextMessage(json);
            for (WebSocketSession session : subscribers) {
                sendQuietly(session, msg);
            }
        } catch (Exception e) {
            System.err.printf("[WS-OPT] Error serializando evento: %s%n", e.getMessage());
        }
    }

    private void sendQuietly(WebSocketSession session, TextMessage msg) {
        if (!session.isOpen()) { subscribers.remove(session); return; }
        try {
            synchronized (session) { session.sendMessage(msg); }
        } catch (IOException e) {
            subscribers.remove(session);
        }
    }

    private String buildEnvelope(OptimizerEventDTO dto) throws Exception {
        Map<String, Object> envelope = Map.of(
                "seq",     seq.getAndIncrement(),
                "type",    eventType(dto),
                "simTime", dto.simTime().toString(),
                "payload", dto);
        return mapper.writeValueAsString(envelope);
    }

    private String eventType(OptimizerEventDTO dto) {
        return switch (dto) {
            case AlgorithmRunDTO   ignored -> "ALGORITHM_RUN";
            case CollapseDetailDTO ignored -> "COLLAPSE_DETAIL";
        };
    }
}
