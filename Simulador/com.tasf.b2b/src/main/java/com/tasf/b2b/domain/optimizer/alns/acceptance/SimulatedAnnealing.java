package com.tasf.b2b.domain.optimizer.alns.acceptance;

import com.tasf.b2b.domain.optimizer.alns.AcceptanceCriterion;

import java.util.Random;

/**
 * Acepta siempre mejoras; acepta soluciones peores con probabilidad exp(-Δ/T).
 * La temperatura decae geometricamente con cada llamada a accept().
 */
public class SimulatedAnnealing implements AcceptanceCriterion {

    private final Random random;
    private final double coolingRate;
    private double       temperature;

    public SimulatedAnnealing(double initialTemperature, double coolingRate, Random random) {
        this.temperature  = initialTemperature;
        this.coolingRate  = coolingRate;
        this.random       = random;
    }

    @Override
    public boolean accept(double candidateScore, double currentScore) {
        if (candidateScore <= currentScore) {
            temperature *= coolingRate;
            return true;
        }
        double delta = candidateScore - currentScore;
        boolean accepted = random.nextDouble() < Math.exp(-delta / temperature);
        temperature *= coolingRate;
        return accepted;
    }
}
