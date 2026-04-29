package com.s4etech.performance.v2.model;

public enum VideoCodec {

    H264("h264", "rtph264depay", "h264parse config-interval=1", "avdec_h264"),
    H265("h265", "rtph265depay", "h265parse config-interval=1", "avdec_h265");

    private final String code;
    private final String depay;
    private final String parser;
    private final String softwareDecoder;

    VideoCodec(String code, String depay, String parser, String softwareDecoder) {
        this.code = code;
        this.depay = depay;
        this.parser = parser;
        this.softwareDecoder = softwareDecoder;
    }

    public String getCode() {
        return code;
    }

    public String getDepay() {
        return depay;
    }

    public String getParser() {
        return parser;
    }

    public String getSoftwareDecoder() {
        return softwareDecoder;
    }

    public static VideoCodec fromCaps(String capsText) {
        if (capsText == null) {
            return null;
        }

        String text = capsText.toUpperCase(java.util.Locale.ROOT);

        if (text.contains("H265") || text.contains("HEVC")) {
            return H265;
        }

        if (text.contains("H264") || text.contains("AVC")) {
            return H264;
        }

        return null;
    }
}