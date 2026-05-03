package com.ext.healthextended.client;

public final class HealthColorHelper {

    private static final int FULL_RED = 0x4CAF50;
    private static final int MID_ORANGE = 0xF39C12;
    private static final int LOW_RED = 0xD64541;

    private HealthColorHelper() {
    }

    public static int getRgb(float hpPercent) {
        float clamped = Math.max(0.0f, Math.min(1.0f, hpPercent));
        if (clamped >= 0.5f) {
            return lerpRgb(MID_ORANGE, FULL_RED, (clamped - 0.5f) / 0.5f);
        }
        return lerpRgb(LOW_RED, MID_ORANGE, clamped / 0.5f);
    }

    public static int getArgb(float hpPercent) {
        return 0xFF000000 | getRgb(hpPercent);
    }

    public static float getRed(float hpPercent) {
        return ((getRgb(hpPercent) >> 16) & 0xFF) / 255.0f;
    }

    public static float getGreen(float hpPercent) {
        return ((getRgb(hpPercent) >> 8) & 0xFF) / 255.0f;
    }

    public static float getBlue(float hpPercent) {
        return (getRgb(hpPercent) & 0xFF) / 255.0f;
    }

    private static int lerpRgb(int from, int to, float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        int fromR = (from >> 16) & 0xFF;
        int fromG = (from >> 8) & 0xFF;
        int fromB = from & 0xFF;
        int toR = (to >> 16) & 0xFF;
        int toG = (to >> 8) & 0xFF;
        int toB = to & 0xFF;

        int red = Math.round(fromR + (toR - fromR) * clamped);
        int green = Math.round(fromG + (toG - fromG) * clamped);
        int blue = Math.round(fromB + (toB - fromB) * clamped);
        return (red << 16) | (green << 8) | blue;
    }
}