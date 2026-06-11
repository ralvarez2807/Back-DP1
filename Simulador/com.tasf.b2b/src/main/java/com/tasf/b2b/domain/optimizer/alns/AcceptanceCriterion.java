package com.tasf.b2b.domain.optimizer.alns;

public interface AcceptanceCriterion {
    boolean accept(double candidateScore, double currentScore);
}
