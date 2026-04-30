package com.s4etech.performance.v2.tuning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.s4etech.performance.v2.model.PipelineTestSummary;

public class RecommendationSelector {

    private static final double MINIMUM_RECOMMENDED_AVERAGE_SCORE = 90.0;
    private static final double MINIMUM_RECOMMENDED_MINIMUM_SCORE = 85.0;
    private static final int MAXIMUM_RECOMMENDED_PEAKS_ABOVE_200_MS = 0;
    private static final int MAXIMUM_RECOMMENDED_ERRORS = 0;
    private static final int MAXIMUM_RECOMMENDED_WATCHDOG = 0;
    private static final long MAXIMUM_INTERVAL_CLOSE_RESULT_TOLERANCE_MS = 15;

    public PipelineTestSummary selectBest(List<PipelineTestSummary> results) {
        if (results == null || results.isEmpty()) {
            return null;
        }

        List<PipelineTestSummary> excellentCandidates = filterRecommended(results);

        if (!excellentCandidates.isEmpty()) {
            return selectByStability(excellentCandidates);
        }

        List<PipelineTestSummary> candidatesWithoutSevereFailure = filterWithoutSevereFailure(results);

        if (!candidatesWithoutSevereFailure.isEmpty()) {
            return selectByStability(candidatesWithoutSevereFailure);
        }

        List<PipelineTestSummary> candidatesWithoutError = filterWithoutError(results);

        if (!candidatesWithoutError.isEmpty()) {
            return selectByStability(candidatesWithoutError);
        }

        return selectByStability(results);
    }

    public List<PipelineTestSummary> rankBestFirst(List<PipelineTestSummary> results) {
        List<PipelineTestSummary> ranked = new ArrayList<>();

        if (results == null) {
            return ranked;
        }

        ranked.addAll(results);
        ranked.sort(this::compareByStability);

        return ranked;
    }

    public boolean isRecommended(PipelineTestSummary summary) {
        if (summary == null) {
            return false;
        }

        if (summary.getAverageScore() < MINIMUM_RECOMMENDED_AVERAGE_SCORE) {
            return false;
        }

        if (summary.getMinimumScore() < MINIMUM_RECOMMENDED_MINIMUM_SCORE) {
            return false;
        }

        if (summary.getTotalPeaksAbove200Ms() > MAXIMUM_RECOMMENDED_PEAKS_ABOVE_200_MS) {
            return false;
        }

        if (summary.getTotalErrors() > MAXIMUM_RECOMMENDED_ERRORS) {
            return false;
        }

        if (summary.getTotalWatchdog() > MAXIMUM_RECOMMENDED_WATCHDOG) {
            return false;
        }

        return true;
    }

    private List<PipelineTestSummary> filterRecommended(List<PipelineTestSummary> results) {
        List<PipelineTestSummary> filtered = new ArrayList<>();

        for (PipelineTestSummary summary : results) {
            if (isRecommended(summary)) {
                filtered.add(summary);
            }
        }

        return filtered;
    }

    private List<PipelineTestSummary> filterWithoutSevereFailure(List<PipelineTestSummary> results) {
        List<PipelineTestSummary> filtered = new ArrayList<>();

        for (PipelineTestSummary summary : results) {
            if (summary.getTotalErrors() == 0
                    && summary.getTotalWatchdog() == 0
                    && summary.getTotalPeaksAbove200Ms() == 0) {
                filtered.add(summary);
            }
        }

        return filtered;
    }

    private List<PipelineTestSummary> filterWithoutError(List<PipelineTestSummary> results) {
        List<PipelineTestSummary> filtered = new ArrayList<>();

        for (PipelineTestSummary summary : results) {
            if (summary.getTotalErrors() == 0 && summary.getTotalWatchdog() == 0) {
                filtered.add(summary);
            }
        }

        return filtered;
    }

    private PipelineTestSummary selectByStability(List<PipelineTestSummary> candidates) {
        return candidates.stream()
                .min(this::compareByStability)
                .orElse(null);
    }

    private int compareByStability(PipelineTestSummary left, PipelineTestSummary right) {
        int comparison = Integer.compare(left.getTotalPeaksAbove200Ms(), right.getTotalPeaksAbove200Ms());

        if (comparison != 0) {
            return comparison;
        }

        comparison = Integer.compare(left.getTotalPeaksAbove120Ms(), right.getTotalPeaksAbove120Ms());

        if (comparison != 0) {
            return comparison;
        }

        comparison = Long.compare(getMaximumIntervalBucket(left), getMaximumIntervalBucket(right));

        if (comparison != 0) {
            return comparison;
        }

        comparison = Integer.compare(left.getConfig().getLatencyMs(), right.getConfig().getLatencyMs());

        if (comparison != 0) {
            return comparison;
        }

        comparison = Integer.compare(left.getConfig().getBufferMs(), right.getConfig().getBufferMs());

        if (comparison != 0) {
            return comparison;
        }

        comparison = Long.compare(left.getWorstMaximumIntervalMs(), right.getWorstMaximumIntervalMs());

        if (comparison != 0) {
            return comparison;
        }

        comparison = Comparator.comparingDouble(PipelineTestSummary::getMinimumScore)
                .reversed()
                .compare(left, right);

        if (comparison != 0) {
            return comparison;
        }

        return Comparator.comparingDouble(PipelineTestSummary::getAverageScore)
                .reversed()
                .compare(left, right);
    }

    private long getMaximumIntervalBucket(PipelineTestSummary summary) {
        return summary.getWorstMaximumIntervalMs() / MAXIMUM_INTERVAL_CLOSE_RESULT_TOLERANCE_MS;
    }
}
