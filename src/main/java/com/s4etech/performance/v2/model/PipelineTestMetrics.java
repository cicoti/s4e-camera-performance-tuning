package com.s4etech.performance.v2.model;

import java.util.concurrent.TimeUnit;

public class PipelineTestMetrics {

    private PipelineTestConfig config;

    private String status = "NAO_INICIADO";
    private String errorMessage;
    private String pipelineText;
    private String gstLaunchCommand;

    private long startTimeNs;
    private long firstFrameTimeNs;
    private long timeToFirstFrameMs;

    private int measuredFrames;

    private int fpsSum;
    private int fpsSampleCount;
    private int minimumFps = Integer.MAX_VALUE;
    private int maximumFps = Integer.MIN_VALUE;

    private long intervalSumMs;
    private int intervalCount;
    private long maximumIntervalMs;

    private int peaksAbove80Ms;
    private int peaksAbove120Ms;
    private int peaksAbove200Ms;

    private int errorCount;
    private int watchdogCount;

    private double score;

    private int framesInCurrentSecond;
    private long lastFpsCalculationNs;
    private long lastMeasuredFrameNs;

    public synchronized void markStarted() {
        startTimeNs = System.nanoTime();
        status = "EXECUTANDO";
    }

    public synchronized void recordFrame(long warmupMs) {
        long nowNs = System.nanoTime();

        if (startTimeNs == 0) {
            return;
        }

        if (firstFrameTimeNs == 0) {
            firstFrameTimeNs = nowNs;
            timeToFirstFrameMs = TimeUnit.NANOSECONDS.toMillis(nowNs - startTimeNs);
        }

        boolean warmingUp = TimeUnit.NANOSECONDS.toMillis(nowNs - startTimeNs) < warmupMs;

        if (warmingUp) {
            return;
        }

        measuredFrames++;
        framesInCurrentSecond++;

        long previousFrameNs = lastMeasuredFrameNs;
        lastMeasuredFrameNs = nowNs;

        if (previousFrameNs > 0) {
            long intervalMs = TimeUnit.NANOSECONDS.toMillis(nowNs - previousFrameNs);
            recordInterval(intervalMs);
        }

        if (lastFpsCalculationNs == 0) {
            lastFpsCalculationNs = nowNs;
            framesInCurrentSecond = 0;
            return;
        }

        if (nowNs - lastFpsCalculationNs >= TimeUnit.SECONDS.toNanos(1)) {
            recordFps(framesInCurrentSecond);
            framesInCurrentSecond = 0;
            lastFpsCalculationNs = nowNs;
        }
    }

    public synchronized void recordError(String message, boolean watchdog) {
        errorCount++;
        status = "ERRO";
        errorMessage = message;

        if (watchdog) {
            watchdogCount++;
        }
    }

    public synchronized void recordWarning(boolean watchdog) {
        if (watchdog) {
            watchdogCount++;
        }
    }

    public synchronized void markEos() {
        status = "EOS";
    }

    public synchronized void markFinishedIfRunning() {
        if (!"ERRO".equals(status) && !"EOS".equals(status)) {
            status = "FINALIZADO";
        }
    }

    public synchronized void calculateScore() {
        if (measuredFrames == 0) {
            score = 0;
            return;
        }

        double calculatedScore = 100.0;

        calculatedScore -= errorCount * 30.0;
        calculatedScore -= watchdogCount * 30.0;
        calculatedScore -= peaksAbove200Ms * 5.0;
        calculatedScore -= peaksAbove120Ms * 2.0;
        calculatedScore -= peaksAbove80Ms * 0.5;

        if (getMinimumFps() > 0 && getMinimumFps() < 20) {
            calculatedScore -= 10.0;
        }

        if (timeToFirstFrameMs > 3000) {
            calculatedScore -= 5.0;
        }

        score = Math.max(0, Math.min(100, calculatedScore));
    }

    private void recordInterval(long intervalMs) {
        intervalSumMs += intervalMs;
        intervalCount++;

        if (intervalMs > maximumIntervalMs) {
            maximumIntervalMs = intervalMs;
        }

        if (intervalMs > 80) {
            peaksAbove80Ms++;
        }

        if (intervalMs > 120) {
            peaksAbove120Ms++;
        }

        if (intervalMs > 200) {
            peaksAbove200Ms++;
        }
    }

    private void recordFps(int fps) {
        fpsSum += fps;
        fpsSampleCount++;

        if (fps < minimumFps) {
            minimumFps = fps;
        }

        if (fps > maximumFps) {
            maximumFps = fps;
        }
    }

    public PipelineTestConfig getConfig() {
        return config;
    }

    public void setConfig(PipelineTestConfig config) {
        this.config = config;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getPipelineText() {
        return pipelineText;
    }

    public void setPipelineText(String pipelineText) {
        this.pipelineText = pipelineText;
    }

    public String getGstLaunchCommand() {
        return gstLaunchCommand;
    }

    public void setGstLaunchCommand(String gstLaunchCommand) {
        this.gstLaunchCommand = gstLaunchCommand;
    }

    public long getTimeToFirstFrameMs() {
        return timeToFirstFrameMs;
    }

    public int getMeasuredFrames() {
        return measuredFrames;
    }

    public double getAverageFps() {
        if (fpsSampleCount == 0) {
            return 0;
        }

        return fpsSum / (double) fpsSampleCount;
    }

    public int getMinimumFps() {
        if (minimumFps == Integer.MAX_VALUE) {
            return 0;
        }

        return minimumFps;
    }

    public int getMaximumFps() {
        if (maximumFps == Integer.MIN_VALUE) {
            return 0;
        }

        return maximumFps;
    }

    public double getAverageIntervalMs() {
        if (intervalCount == 0) {
            return 0;
        }

        return intervalSumMs / (double) intervalCount;
    }

    public long getMaximumIntervalMs() {
        return maximumIntervalMs;
    }

    public int getPeaksAbove80Ms() {
        return peaksAbove80Ms;
    }

    public int getPeaksAbove120Ms() {
        return peaksAbove120Ms;
    }

    public int getPeaksAbove200Ms() {
        return peaksAbove200Ms;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public int getWatchdogCount() {
        return watchdogCount;
    }

    public double getScore() {
        return score;
    }

    @Override
    public String toString() {
        return "PipelineTestMetrics{"
                + "camera=" + (config != null && config.getCameraConfig() != null ? config.getCameraConfig().getCode() : null)
                + ", status='" + status + '\''
                + ", averageFps=" + String.format(java.util.Locale.US, "%.2f", getAverageFps())
                + ", minimumFps=" + getMinimumFps()
                + ", maximumIntervalMs=" + maximumIntervalMs
                + ", peaksAbove80Ms=" + peaksAbove80Ms
                + ", peaksAbove120Ms=" + peaksAbove120Ms
                + ", peaksAbove200Ms=" + peaksAbove200Ms
                + ", errors=" + errorCount
                + ", watchdog=" + watchdogCount
                + ", timeToFirstFrameMs=" + timeToFirstFrameMs
                + ", score=" + String.format(java.util.Locale.US, "%.2f", score)
                + '}';
    }
}
