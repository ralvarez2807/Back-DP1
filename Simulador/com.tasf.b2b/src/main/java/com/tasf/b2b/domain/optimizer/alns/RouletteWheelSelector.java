package com.tasf.b2b.domain.optimizer.alns;

import java.util.List;
import java.util.Random;

/**
 * Selección proporcional a pesos con decaimiento exponencial.
 * select() registra internamente el último índice; reward() actualiza ese peso.
 */
public class RouletteWheelSelector<T> {

    private final List<T> operators;
    private final double[] weights;
    private final double   decayFactor;
    private final Random   random;
    private       int      lastIndex;

    public RouletteWheelSelector(List<T> operators, Random random) {
        this.operators   = operators;
        this.weights     = new double[operators.size()];
        this.decayFactor = 0.8;
        this.random      = random;
        this.lastIndex   = 0;
        for (int i = 0; i < weights.length; i++) weights[i] = 1.0;
    }

    public T select() {
        double total = 0.0;
        for (double w : weights) total += w;

        double r   = random.nextDouble() * total;
        double cum = 0.0;
        for (int i = 0; i < weights.length; i++) {
            cum += weights[i];
            if (r <= cum) {
                lastIndex = i;
                return operators.get(i);
            }
        }
        lastIndex = weights.length - 1;
        return operators.get(lastIndex);
    }

    public void reward(double score) {
        weights[lastIndex] = decayFactor * weights[lastIndex] + (1 - decayFactor) * score;
        if (weights[lastIndex] < 0.01) weights[lastIndex] = 0.01;
    }
}
