package com.tasf.b2b.presentation.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tasf.b2b.domain.simulator.StatePublisher;
import com.tasf.b2b.domain.simulator.dto.*;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * StatePublisher sin dependencias externas.
 *
 * El runner deposita DTOs en la BlockingQueue (no-bloqueante).
 * Un hilo daemon los toma de uno en uno y los reenvía a todos
 * los WebSocketSession suscritos en ese momento.
 *
 * Con 3 clientes y eventos del orden de uno por segundo el hilo
 * drenador nunca tiene backlog apreciable.
 */
public class InMemoryStatePublisher implements StatePublisher {

    private final BlockingQueue<StateChangeDTO>          queue       = new LinkedBlockingQueue<>();
    private final CopyOnWriteArrayList<WebSocketSession> subscribers = new CopyOnWriteArrayList<>();
    private final AtomicLong                             seq         = new AtomicLong(0);
    private final Thread                                 drainThread;
    private final ObjectMapper                           mapper;

    public InMemoryStatePublisher(String sessionId) {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.drainThread = new Thread(this::drain, "ws-drain-" + sessionId);
        this.drainThread.setDaemon(true);
        this.drainThread.start();
    }

    // ── StatePublisher ────────────────────────────────────────────────────────

    /** Llamado desde el hilo de simulación. No bloquea nunca. */
    @Override
    public void publish(StateChangeDTO dto) {
        queue.offer(dto);
    }

    /** Llamado desde SimulationSession.interruptAll(). */
    @Override
    public void close() {
        drainThread.interrupt();
    }

    // ── Suscripción WebSocket ─────────────────────────────────────────────────

    public void subscribe(WebSocketSession session) {
        subscribers.add(session);
    }

    public void unsubscribe(WebSocketSession session) {
        subscribers.remove(session);
    }

    // ── Hilo drenador ─────────────────────────────────────────────────────────

    private void drain() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                StateChangeDTO dto = queue.take();
                String json = buildEnvelope(dto);
                TextMessage msg = new TextMessage(json);
                for (WebSocketSession session : subscribers) {
                    sendQuietly(session, msg);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.printf("[WS] Error serializando DTO: %s%n", e.getMessage());
            }
        }
    }

    private void sendQuietly(WebSocketSession session, TextMessage msg) {
        if (!session.isOpen()) {
            subscribers.remove(session);
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(msg);
            }
        } catch (IOException e) {
            subscribers.remove(session);
        }
    }

    // ── Serialización ─────────────────────────────────────────────────────────

    private String buildEnvelope(StateChangeDTO dto) throws Exception {
        Map<String, Object> envelope = Map.of(
                "seq",     seq.getAndIncrement(),
                "type",    eventType(dto),
                "simTime", dto.simTime().toString(),
                "payload", dto);
        return mapper.writeValueAsString(envelope);
    }

    private String eventType(StateChangeDTO dto) {
        return switch (dto) {
            case FlightScheduledDTO  ignored -> "FLIGHT_SCHEDULED";
            case FlightDepartedDTO   ignored -> "FLIGHT_DEPARTED";
            case FlightArrivedDTO    ignored -> "FLIGHT_ARRIVED";
            case FlightCancelledDTO  ignored -> "FLIGHT_CANCELLED";
            case BaggageDepartedDTO  ignored -> "BAGGAGE_DEPARTED";
            case BaggageArrivedDTO   ignored -> "BAGGAGE_ARRIVED";
            case BaggageDeliveredDTO ignored -> "BAGGAGE_DELIVERED";
            case BaggagePendingDTO   ignored -> "BAGGAGE_PENDING";
            case BaggageAssignedDTO  ignored -> "BAGGAGE_ASSIGNED";
            case ShipmentCreatedDTO  ignored -> "SHIPMENT_CREATED";
        };
    }
}
