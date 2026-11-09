package com.aerocause.service.fingerprinting;

import com.aerocause.domain.Hypothesis.HypothesisType;

/**
 * A signed contribution from one fingerprinting rule to one hypothesis type.
 *
 * @param hypothesisType  which hypothesis this supports or weakens
 * @param score           positive = supports, negative = weakens (range approx -1..1)
 * @param evidenceId      unique evidence item ID (e.g. "EV-001")
 * @param summary         human-readable evidence description
 * @param missingEvidence description of missing data, or null if all inputs were available
 */
public record RuleContribution(
        HypothesisType hypothesisType,
        double score,
        String evidenceId,
        String summary,
        String missingEvidence
) {
    public boolean hasMissingEvidence() {
        return missingEvidence != null && !missingEvidence.isBlank();
    }
    public boolean isFor()     { return score > 0; }
    public boolean isAgainst() { return score < 0; }
}
