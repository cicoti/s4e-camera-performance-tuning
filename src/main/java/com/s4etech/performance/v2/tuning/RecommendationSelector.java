package com.s4etech.performance.v2.tuning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.s4etech.performance.v2.model.PipelineTestMetrics;

public class RecommendationSelector {

    private static final double MINIMUM_RECOMMENDED_SCORE = 90.0;
    private static final int MAXIMUM_RECOMMENDED_PEAKS_ABOVE_200_MS = 0;
    private static final int MAXIMUM_RECOMMENDED_ERRORS = 0;
    private static final int MAXIMUM_RECOMMENDED_WATCHDOG = 0;
    private static final long MAXIMUM_INTERVAL_CLOSE_RESULT_TOLERANCE_MS = 15;

    public PipelineTestMetrics selectBest(List<PipelineTestMetrics> results) {
        if (results == null || results.isEmpty()) {
            return null;
        }

        List<PipelineTestMetrics> excellentCandidates = filterRecommended(results);

        if (!excellentCandidates.isEmpty()) {
            return selectByStability(excellentCandidates);
        }

        List<PipelineTestMetrics> candidatesWithoutSevereFailure = filterWithoutSevereFailure(results);

        if (!candidatesWithoutSevereFailure.isEmpty()) {
            return selectByStability(candidatesWithoutSevereFailure);
        }

        List<PipelineTestMetrics> candidatesWithoutError = filterWithoutError(results);

        if (!candidatesWithoutError.isEmpty()) {
            return selectByStability(candidatesWithoutError);
        }

        return selectByStability(results);
    }

    public List<PipelineTestMetrics> rankBestFirst(List<PipelineTestMetrics> results) {
        List<PipelineTestMetrics> ranked = new ArrayList<>();

        if (results == null) {
            return ranked;
        }

        ranked.addAll(results);
        ranked.sort(this::compareByStability);

        return ranked;
    }

    public boolean isRecommended(PipelineTestMetrics metrics) {
        if (metrics == null) {
            return false;
        }

        if (metrics.getScore() < MINIMUM_RECOMMENDED_SCORE) {
            return false;
        }

        if (metrics.getPeaksAbove200Ms() > MAXIMUM_RECOMMENDED_PEAKS_ABOVE_200_MS) {
            return false;
        }

        if (metrics.getErrorCount() > MAXIMUM_RECOMMENDED_ERRORS) {
            return false;
        }

        if (metrics.getWatchdogCount() > MAXIMUM_RECOMMENDED_WATCHDOG) {
            return false;
        }

        return true;
    }

    private List<PipelineTestMetrics> filterRecommended(List<PipelineTestMetrics> results) {
        List<PipelineTestMetrics> filtered = new ArrayList<>();

        for (PipelineTestMetrics metrics : results) {
            if (isRecommended(metrics)) {
                filtered.add(metrics);
            }
        }

        return filtered;
    }

    private List<PipelineTestMetrics> filterWithoutSevereFailure(List<PipelineTestMetrics> results) {
        List<PipelineTestMetrics> filtered = new ArrayList<>();

        for (PipelineTestMetrics metrics : results) {
            if (metrics.getErrorCount() == 0
                    && metrics.getWatchdogCount() == 0
                    && metrics.getPeaksAbove200Ms() == 0) {
                filtered.add(metrics);
            }
        }

        return filtered;
    }

    private List<PipelineTestMetrics> filterWithoutError(List<PipelineTestMetrics> results) {
        List<PipelineTestMetrics> filtered = new ArrayList<>();

        for (PipelineTestMetrics metrics : results) {
            if (metrics.getErrorCount() == 0 && metrics.getWatchdogCount() == 0) {
                filtered.add(metrics);
            }
        }

        return filtered;
    }

    private PipelineTestMetrics selectByStability(List<PipelineTestMetrics> candidates) {
        return candidates.stream()
                .min(this::compareByStability)
                .orElse(null);
    }

    private int compareByStability(PipelineTestMetrics left, PipelineTestMetrics right) {
        int comparison = Integer.compare(left.getPeaksAbove200Ms(), right.getPeaksAbove200Ms());

        if (comparison != 0) {
            return comparison;
        }

        comparison = Integer.compare(left.getPeaksAbove120Ms(), right.getPeaksAbove120Ms());

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

        comparison = Long.compare(left.getMaximumIntervalMs(), right.getMaximumIntervalMs());

        if (comparison != 0) {
            return comparison;
        }

        return Comparator.comparingDouble(PipelineTestMetrics::getScore)
                .reversed()
                .compare(left, right);
    }

    private long getMaximumIntervalBucket(PipelineTestMetrics metrics) {
        return metrics.getMaximumIntervalMs() / MAXIMUM_INTERVAL_CLOSE_RESULT_TOLERANCE_MS;
    }
}
