package com.s4etech.performance.v2.model;

public class StreamDiscoveryResult {

    private CameraConfig camera;
    private VideoCodec codec;
    private ProtocolDiscoveryResult tcpResult;
    private ProtocolDiscoveryResult udpResult;

    public CameraConfig getCamera() {
        return camera;
    }

    public void setCamera(CameraConfig camera) {
        this.camera = camera;
    }

    public VideoCodec getCodec() {
        return codec;
    }

    public void setCodec(VideoCodec codec) {
        this.codec = codec;
    }

    public ProtocolDiscoveryResult getTcpResult() {
        return tcpResult;
    }

    public void setTcpResult(ProtocolDiscoveryResult tcpResult) {
        this.tcpResult = tcpResult;
    }

    public ProtocolDiscoveryResult getUdpResult() {
        return udpResult;
    }

    public void setUdpResult(ProtocolDiscoveryResult udpResult) {
        this.udpResult = udpResult;
    }

    public boolean isTcpAvailable() {
        return tcpResult != null && tcpResult.isAvailable();
    }

    public boolean isUdpAvailable() {
        return udpResult != null && udpResult.isAvailable();
    }

    public boolean hasAnyProtocolAvailable() {
        return isTcpAvailable() || isUdpAvailable();
    }

    public RtspProtocol getPreferredPreviewProtocol() {
        if (isTcpAvailable()) {
            return RtspProtocol.TCP;
        }

        if (isUdpAvailable()) {
            return RtspProtocol.UDP;
        }

        return RtspProtocol.TCP;
    }

    public VideoCodec getCodecOrFallback() {
        if (codec != null) {
            return codec;
        }

        return VideoCodec.H264;
    }

    @Override
    public String toString() {
        return "StreamDiscoveryResult{" +
                "camera=" + camera +
                ", codec=" + getCodecOrFallback().getCode() +
                ", tcpAvailable=" + isTcpAvailable() +
                ", udpAvailable=" + isUdpAvailable() +
                '}';
    }
}