package com.tasf.b2b.application.usecase;

import com.tasf.b2b.domain.model.graph.SpaceTimeGraph;
import com.tasf.b2b.domain.optimizer.metrics.OptimizerPublisher;
import com.tasf.b2b.domain.simulator.SimulationConfig;
import com.tasf.b2b.domain.simulator.SimulationRunner;
import com.tasf.b2b.domain.simulator.StatePublisher;

import java.time.Instant;
import java.util.List;

/**
 * Contenedor de todos los recursos asociados a una sesión de simulación activa.
 *
 * Una sesión nace cuando alguien llama start() y muere cuando termina la
 * simulación o cuando se llama stop(). El registry la mantiene viva entre medias.
 *
 * No es un record porque status cambia durante la vida de la sesión.
 */
public class SimulationSession {

    /** Estados posibles del ciclo de vida de una sesión. */
    public enum SimStatus { STARTING, RUNNING, PAUSED, COMPLETED, STOPPED }

    private final String            id;
    private final String            username;
    private final SimulationRunner  runner;
    private final SpaceTimeGraph    graph;
    private final SimulationConfig  config;
    private final Instant           startedAt;
    private final StatePublisher    publisher;
    private final OptimizerPublisher optimizerPublisher;

    private List<Thread> allThreads;

    private volatile SimStatus status;

    public SimulationSession(String id,
                             String username,
                             SimulationRunner runner,
                             SpaceTimeGraph graph,
                             SimulationConfig config,
                             StatePublisher publisher,
                             OptimizerPublisher optimizerPublisher) {
        this.id                 = id;
        this.username           = username;
        this.runner             = runner;
        this.graph              = graph;
        this.config             = config;
        this.publisher          = publisher;
        this.optimizerPublisher = optimizerPublisher;
        this.startedAt          = Instant.now();
        this.status             = SimStatus.STARTING;
    }

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    public void interruptAll() {
        if (allThreads != null) allThreads.forEach(Thread::interrupt);
        publisher.close();
        optimizerPublisher.close();
        this.status = SimStatus.STOPPED;
    }

    // ── Getters / setters ────────────────────────────────────────────────────

    public String            getId()                { return id; }
    public String            getUsername()          { return username; }
    public SimulationRunner  getRunner()            { return runner; }
    public SpaceTimeGraph    getGraph()             { return graph; }
    public SimulationConfig  getConfig()            { return config; }
    public Instant           getStartedAt()         { return startedAt; }
    public SimStatus         getStatus()            { return status; }
    public StatePublisher    getPublisher()         { return publisher; }
    public OptimizerPublisher getOptimizerPublisher() { return optimizerPublisher; }

    public void setStatus(SimStatus status)           { this.status = status; }
    public void setAllThreads(List<Thread> threads)   { this.allThreads = threads; }
}
