package com.s4etech.performance.v2.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PipelineTestSummary {

    private final PipelineTestConfig config;
    private final List<PipelineTestMetrics> runs;

    private int executionCount;
    private String status;
    private String errorMessage;

    private double averageFps;
    private int minimumFps;
    private int maximumFps;

    private double averageIntervalMs;
    private long worstMaximumIntervalMs;

    private int totalPeaksAbove80Ms;
    private int totalPeaksAbove120Ms;
    private int totalPeaksAbove200Ms;

    private int totalErrors;
    private int totalWatchdog;

    private double averageTimeToFirstFrameMs;
    private double averageScore;
    private double minimumScore;

    public PipelineTestSummary(PipelineTestConfig config, List<PipelineTestMetrics> runs) {
        this.config = config;
        this.runs = new ArrayList<>(runs);
        consolidate();
    }

    private void consolidate() {
        executionCount = runs.size();

        if (runs.isEmpty()) {
            status = "SEM_EXECUCOES";
            minimumFps = 0;
            maximumFps = 0;
            minimumScore = 0;
            return;
        }

        double fpsSum = 0;
        double intervalSum = 0;
        double timeToFirstFrameSum = 0;
        double scoreSum = 0;

        minimumFps = Integer.MAX_VALUE;
        maximumFps = Integer.MIN_VALUE;
        minimumScore = Double.MAX_VALUE;
        status = "CONSOLIDADO";

        for (PipelineTestMetrics metrics : runs) {
            fpsSum += metrics.getAverageFps();
            intervalSum += metrics.getAverageIntervalMs();
            timeToFirstFrameSum += metrics.getTimeToFirstFrameMs();
            scoreSum += metrics.getScore();

            minimumFps = Math.min(minimumFps, metrics.getMinimumFps());
            maximumFps = Math.max(maximumFps, metrics.getMaximumFps());
            worstMaximumIntervalMs = Math.max(worstMaximumIntervalMs, metrics.getMaximumIntervalMs());

            totalPeaksAbove80Ms += metrics.getPeaksAbove80Ms();
            totalPeaksAbove120Ms += metrics.getPeaksAbove120Ms();
            totalPeaksAbove200Ms += metrics.getPeaksAbove200Ms();
            totalErrors += metrics.getErrorCount();
            totalWatchdog += metrics.getWatchdogCount();

            minimumScore = Math.min(minimumScore, metrics.getScore());

            if (errorMessage == null && metrics.getErrorMessage() != null) {
                errorMessage = metrics.getErrorMessage();
            }
        }

        averageFps = fpsSum / executionCount;
        averageIntervalMs = intervalSum / executionCount;
        averageTimeToFirstFrameMs = timeToFirstFrameSum / executionCount;
        averageScore = scoreSum / executionCount;

        if (minimumFps == Integer.MAX_VALUE) {
            minimumFps = 0;
        }

        if (maximumFps == Integer.MIN_VALUE) {
            maximumFps = 0;
        }

        if (minimumScore == Double.MAX_VALUE) {
            minimumScore = 0;
        }

        if (totalErrors > 0) {
            status = "CONSOLIDADO_COM_ERRO";
        }
    }

    public PipelineTestConfig getConfig() {
        return config;
    }

    public List<PipelineTestMetrics> getRuns() {
        return Collections.unmodifiableList(runs);
    }

    public int getExecutionCount() {
        return executionCount;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public double getAverageFps() {
        return averageFps;
    }

    public int getMinimumFps() {
        return minimumFps;
    }

    public int getMaximumFps() {
        return maximumFps;
    }

    public double getAverageIntervalMs() {
        return averageIntervalMs;
    }

    public long getWorstMaximumIntervalMs() {
        return worstMaximumIntervalMs;
    }

    public int getTotalPeaksAbove80Ms() {
        return totalPeaksAbove80Ms;
    }

    public int getTotalPeaksAbove120Ms() {
        return totalPeaksAbove120Ms;
    }

    public int getTotalPeaksAbove200Ms() {
        return totalPeaksAbove200Ms;
    }

    public int getTotalErrors() {
        return totalErrors;
    }

    public int getTotalWatchdog() {
        return totalWatchdog;
    }

    public double getAverageTimeToFirstFrameMs() {
        return averageTimeToFirstFrameMs;
    }

    public double getAverageScore() {
        return averageScore;
    }

    public double getMinimumScore() {
        return minimumScore;
    }

    @Override
    public String toString() {
        return "PipelineTestSummary{"
                + "camera=" + (config != null && config.getCameraConfig() != null ? config.getCameraConfig().getCode() : null)
                + ", status='" + status + '\''
                + ", executions=" + executionCount
                + ", averageFps=" + String.format(java.util.Locale.US, "%.2f", averageFps)
                + ", minimumFps=" + minimumFps
                + ", worstMaximumIntervalMs=" + worstMaximumIntervalMs
                + ", totalPeaksAbove80Ms=" + totalPeaksAbove80Ms
                + ", totalPeaksAbove120Ms=" + totalPeaksAbove120Ms
                + ", totalPeaksAbove200Ms=" + totalPeaksAbove200Ms
                + ", totalErrors=" + totalErrors
                + ", totalWatchdog=" + totalWatchdog
                + ", averageTimeToFirstFrameMs=" + String.format(java.util.Locale.US, "%.2f", averageTimeToFirstFrameMs)
                + ", averageScore=" + String.format(java.util.Locale.US, "%.2f", averageScore)
                + ", minimumScore=" + String.format(java.util.Locale.US, "%.2f", minimumScore)
                + '}';
    }
}
