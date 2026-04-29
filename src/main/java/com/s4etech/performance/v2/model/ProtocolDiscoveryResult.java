package com.s4etech.performance.v2.model;

public class ProtocolDiscoveryResult {

    private RtspProtocol protocol;
    private boolean available;
    private boolean streamResponded;
    private VideoCodec codec;
    private String caps;
    private String message;

    public RtspProtocol getProtocol() {
        return protocol;
    }

    public void setProtocol(RtspProtocol protocol) {
        this.protocol = protocol;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public boolean isStreamResponded() {
        return streamResponded;
    }

    public void setStreamResponded(boolean streamResponded) {
        this.streamResponded = streamResponded;
    }

    public VideoCodec getCodec() {
        return codec;
    }

    public void setCodec(VideoCodec codec) {
        this.codec = codec;
    }

    public String getCaps() {
        return caps;
    }

    public void setCaps(String caps) {
        this.caps = caps;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}