package com.example;

public final class PvoConfig {

    public static final double TARGET_RANGE = 128.0;
    public static final double CONE_ANGLE_DEGREES = 30.0;
    public static final int SEARCH_INTERVAL_TICKS = 3;
    public static final int ARM_DELAY_TICKS = 3;
    public static final double PROXIMITY_RADIUS = 1.0;
    public static final double INITIAL_SPEED = 0.5;
    public static final double MAX_SPEED = 4.5;
    public static final int THRUST_DURATION_TICKS = 35;
    public static final double MAX_TURN_DEGREES_PER_TICK = 1.2;
    public static final int LOST_TARGET_MEMORY_TICKS = 30;
    public static final double DRAG_AFTER_THRUST = 0.995;
    public static final double MAX_LEAD_TICKS = 10.0;
    public static final int MAX_LIFETIME_TICKS = 120;
    public static final boolean DEBUG = false;

    public static final double MAX_TURN_RAD_PER_TICK = MAX_TURN_DEGREES_PER_TICK * Math.PI / 180.0;
    public static final double CONE_COS = Math.cos(Math.toRadians(CONE_ANGLE_DEGREES));
    public static final double LATERAL_ACCEL = MAX_SPEED * MAX_TURN_RAD_PER_TICK;

    private PvoConfig() {
    }
}