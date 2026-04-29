package com.s4etech.performance.v2.model;

public enum RtspProtocol {

    TCP("tcp", 4),
    UDP("udp", 1);

    private final String gstName;
    private final int gstValue;

    RtspProtocol(String gstName, int gstValue) {
        this.gstName = gstName;
        this.gstValue = gstValue;
    }

    public String getGstName() {
        return gstName;
    }

    public int getGstValue() {
        return gstValue;
    }
}