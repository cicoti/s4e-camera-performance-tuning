package com.s4etech.performance.v2.tuning;

import java.util.ArrayList;
import java.util.List;

import com.s4etech.performance.v2.model.PipelineTestConfig;
import com.s4etech.performance.v2.model.RtspProtocol;
import com.s4etech.performance.v2.model.StreamDiscoveryResult;
import com.s4etech.performance.v2.model.VideoCodec;

public class CandidateConfigGenerator {

    private static final int BASE_DURATION_SECONDS = 8;
    private static final int REFINEMENT_DURATION_SECONDS = 10;
    private static final int WATCHDOG_TIMEOUT_MS = 4000;
    private static final int OUTPUT_WIDTH = 640;
    private static final int OUTPUT_HEIGHT = 360;

    private static final int[][] REFINEMENT_LATENCY_BUFFER_PAIRS = {
            {50, 100},
            {50, 150},
            {50, 200},
            {100, 100},
            {100, 150},
            {100, 200},
            {200, 300},
            {300, 500},
            {500, 1000}
    };

    public List<PipelineTestConfig> generateBaseCandidates(StreamDiscoveryResult discoveryResult) {
        List<PipelineTestConfig> candidates = new ArrayList<>();

        if (discoveryResult.isTcpAvailable()) {
            addHardwareAndSoftwareCandidates(candidates, discoveryResult, RtspProtocol.TCP, 100, 200, BASE_DURATION_SECONDS);
        }

        if (discoveryResult.isUdpAvailable()) {
            addHardwareAndSoftwareCandidates(candidates, discoveryResult, RtspProtocol.UDP, 100, 200, BASE_DURATION_SECONDS);
        }

        return candidates;
    }

    public List<PipelineTestConfig> generateCompleteCandidates(StreamDiscoveryResult discoveryResult) {
        List<PipelineTestConfig> candidates = new ArrayList<>();

        if (discoveryResult.isTcpAvailable()) {
            addAllLatencyBufferCandidates(candidates, discoveryResult, RtspProtocol.TCP, true);
            addAllLatencyBufferCandidates(candidates, discoveryResult, RtspProtocol.TCP, false);
        }

        if (discoveryResult.isUdpAvailable()) {
            addAllLatencyBufferCandidates(candidates, discoveryResult, RtspProtocol.UDP, true);
            addAllLatencyBufferCandidates(candidates, discoveryResult, RtspProtocol.UDP, false);
        }

        return candidates;
    }

    public List<PipelineTestConfig> generateRefinementCandidates(PipelineTestConfig baseConfig) {
        return generateLatencyBufferCandidates(
                baseConfig,
                baseConfig.isHardwareAcceleration(),
                REFINEMENT_DURATION_SECONDS
        );
    }

    public List<PipelineTestConfig> generateAlternativeAccelerationCandidates(PipelineTestConfig baseConfig) {
        return generateLatencyBufferCandidates(
                baseConfig,
                !baseConfig.isHardwareAcceleration(),
                REFINEMENT_DURATION_SECONDS
        );
    }

    private List<PipelineTestConfig> generateLatencyBufferCandidates(
            PipelineTestConfig baseConfig,
            boolean hardwareAcceleration,
            int durationSeconds) {

        List<PipelineTestConfig> candidates = new ArrayList<>();

        for (int[] pair : REFINEMENT_LATENCY_BUFFER_PAIRS) {
            candidates.add(createCandidate(
                    baseConfig,
                    baseConfig.getProtocol(),
                    pair[0],
                    pair[1],
                    hardwareAcceleration,
                    durationSeconds
            ));
        }

        return candidates;
    }

    private void addHardwareAndSoftwareCandidates(
            List<PipelineTestConfig> candidates,
            StreamDiscoveryResult discoveryResult,
            RtspProtocol protocol,
            int latencyMs,
            int bufferMs,
            int durationSeconds) {

        PipelineTestConfig baseConfig = new PipelineTestConfig();
        baseConfig.setCameraConfig(discoveryResult.getCamera());
        baseConfig.setVideoCodec(discoveryResult.getCodecOrFallback());

        candidates.add(createCandidate(baseConfig, protocol, latencyMs, bufferMs, true, durationSeconds));
        candidates.add(createCandidate(baseConfig, protocol, latencyMs, bufferMs, false, durationSeconds));
    }

    private void addAllLatencyBufferCandidates(
            List<PipelineTestConfig> candidates,
            StreamDiscoveryResult discoveryResult,
            RtspProtocol protocol,
            boolean hardwareAcceleration) {

        PipelineTestConfig baseConfig = new PipelineTestConfig();
        baseConfig.setCameraConfig(discoveryResult.getCamera());
        baseConfig.setVideoCodec(discoveryResult.getCodecOrFallback());

        for (int[] pair : REFINEMENT_LATENCY_BUFFER_PAIRS) {
            candidates.add(createCandidate(
                    baseConfig,
                    protocol,
                    pair[0],
                    pair[1],
                    hardwareAcceleration,
                    REFINEMENT_DURATION_SECONDS
            ));
        }
    }

    private PipelineTestConfig createCandidate(
            PipelineTestConfig baseConfig,
            RtspProtocol protocol,
            int latencyMs,
            int bufferMs,
            boolean hardwareAcceleration,
            int durationSeconds) {

        VideoCodec codec = baseConfig.getVideoCodec() != null ? baseConfig.getVideoCodec() : VideoCodec.H264;

        return new PipelineTestConfig(
                baseConfig.getCameraConfig(),
                protocol,
                codec,
                hardwareAcceleration,
                resolveDecoder(codec, hardwareAcceleration),
                latencyMs,
                bufferMs,
                WATCHDOG_TIMEOUT_MS,
                OUTPUT_WIDTH,
                OUTPUT_HEIGHT,
                durationSeconds
        );
    }

    private String resolveDecoder(VideoCodec codec, boolean hardwareAcceleration) {
        if (hardwareAcceleration) {
            return "d3d11" + codec.getCode() + "dec";
        }

        return codec.getSoftwareDecoder();
    }
}
