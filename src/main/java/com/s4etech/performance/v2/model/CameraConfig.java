package com.s4etech.performance.v2.model;

public class CameraConfig {

    private final String code;
    private final String rtspUrl;

    public CameraConfig(String code, String rtspUrl) {
        this.code = code;
        this.rtspUrl = rtspUrl;
    }

    public String getCode() {
        return code;
    }

    public String getRtspUrl() {
        return rtspUrl;
    }

    public String getMaskedRtspUrl() {
        if (rtspUrl == null) {
            return "";
        }

        return rtspUrl.replaceAll("rtsp://([^:]+):([^@]+)@", "rtsp://$1:******@");
    }

    @Override
    public String toString() {
        return "CameraConfig{" +
                "code='" + code + '\'' +
                ", rtspUrl='" + getMaskedRtspUrl() + '\'' +
                '}';
    }
}