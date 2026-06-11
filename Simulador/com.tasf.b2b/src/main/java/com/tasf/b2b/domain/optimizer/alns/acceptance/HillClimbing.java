package com.tasf.b2b.domain.optimizer.alns.acceptance;

import com.tasf.b2b.domain.optimizer.alns.AcceptanceCriterion;

public class HillClimbing implements AcceptanceCriterion {
    @Override
    public boolean accept(double candidateScore, double currentScore) {
        return candidateScore <= currentScore;
    }
}
