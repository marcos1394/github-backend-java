package com.quhealthy.social_service.dto.ai;

public enum VideoAspectRatio {
    LANDSCAPE_16_9("16:9"),
    PORTRAIT_9_16("9:16");

    private final String value;

    VideoAspectRatio(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}