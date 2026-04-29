package com.s4etech.performance.v2.model;

public class PipelineTestConfig {

    private CameraConfig cameraConfig;

    private RtspProtocol protocol;

    private VideoCodec videoCodec;

    private boolean hardwareAcceleration;

    private String decoderElement;

    private int latencyMs;

    private int bufferMs;

    private int watchdogTimeoutMs;

    private int outputWidth;

    private int outputHeight;

    private int testDurationSeconds;

    public PipelineTestConfig() {
    }

    public PipelineTestConfig(
            CameraConfig cameraConfig,
            RtspProtocol protocol,
            VideoCodec videoCodec,
            boolean hardwareAcceleration,
            String decoderElement,
            int latencyMs,
            int bufferMs,
            int watchdogTimeoutMs,
            int outputWidth,
            int outputHeight,
            int testDurationSeconds) {

        this.cameraConfig = cameraConfig;
        this.protocol = protocol;
        this.videoCodec = videoCodec;
        this.hardwareAcceleration = hardwareAcceleration;
        this.decoderElement = decoderElement;
        this.latencyMs = latencyMs;
        this.bufferMs = bufferMs;
        this.watchdogTimeoutMs = watchdogTimeoutMs;
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        this.testDurationSeconds = testDurationSeconds;
    }

    public CameraConfig getCameraConfig() {
        return cameraConfig;
    }

    public void setCameraConfig(CameraConfig cameraConfig) {
        this.cameraConfig = cameraConfig;
    }

    public RtspProtocol getProtocol() {
        return protocol;
    }

    public void setProtocol(RtspProtocol protocol) {
        this.protocol = protocol;
    }

    public VideoCodec getVideoCodec() {
        return videoCodec;
    }

    public void setVideoCodec(VideoCodec videoCodec) {
        this.videoCodec = videoCodec;
    }

    public boolean isHardwareAcceleration() {
        return hardwareAcceleration;
    }

    public void setHardwareAcceleration(boolean hardwareAcceleration) {
        this.hardwareAcceleration = hardwareAcceleration;
    }

    public String getDecoderElement() {
        return decoderElement;
    }

    public void setDecoderElement(String decoderElement) {
        this.decoderElement = decoderElement;
    }

    public int getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(int latencyMs) {
        this.latencyMs = latencyMs;
    }

    public int getBufferMs() {
        return bufferMs;
    }

    public void setBufferMs(int bufferMs) {
        this.bufferMs = bufferMs;
    }

    public int getWatchdogTimeoutMs() {
        return watchdogTimeoutMs;
    }

    public void setWatchdogTimeoutMs(int watchdogTimeoutMs) {
        this.watchdogTimeoutMs = watchdogTimeoutMs;
    }

    public int getOutputWidth() {
        return outputWidth;
    }

    public void setOutputWidth(int outputWidth) {
        this.outputWidth = outputWidth;
    }

    public int getOutputHeight() {
        return outputHeight;
    }

    public void setOutputHeight(int outputHeight) {
        this.outputHeight = outputHeight;
    }

    public int getTestDurationSeconds() {
        return testDurationSeconds;
    }

    public void setTestDurationSeconds(int testDurationSeconds) {
        this.testDurationSeconds = testDurationSeconds;
    }

    public String getAccelerationDescription() {
        return hardwareAcceleration ? "hardware" : "software";
    }

    public String getShortDescription() {
        return "camera=" + (cameraConfig != null ? cameraConfig.getCode() : null)
                + ", protocol=" + protocol
                + ", codec=" + videoCodec
                + ", acceleration=" + getAccelerationDescription()
                + ", decoder=" + decoderElement
                + ", latencyMs=" + latencyMs
                + ", bufferMs=" + bufferMs
                + ", watchdogTimeoutMs=" + watchdogTimeoutMs
                + ", output=" + outputWidth + "x" + outputHeight
                + ", durationSeconds=" + testDurationSeconds;
    }

    @Override
    public String toString() {
        return "PipelineTestConfig{"
                + "camera=" + (cameraConfig != null ? cameraConfig.getCode() : null)
                + ", protocol=" + protocol
                + ", videoCodec=" + videoCodec
                + ", hardwareAcceleration=" + hardwareAcceleration
                + ", decoderElement='" + decoderElement + '\''
                + ", latencyMs=" + latencyMs
                + ", bufferMs=" + bufferMs
                + ", watchdogTimeoutMs=" + watchdogTimeoutMs
                + ", outputWidth=" + outputWidth
                + ", outputHeight=" + outputHeight
                + ", testDurationSeconds=" + testDurationSeconds
                + '}';
    }
}
