package com.verisonder.sonderassist.detect

import kotlin.math.sqrt

/**
 * One reading from the motion sensors.
 *
 * Deliberately a plain value with no Android in it. The detector consumes these and
 * nothing else, so the same code runs against the live sensor stream on the phone and
 * against a recorded trace in a unit test. That is the whole reason the detector is
 * testable at all — a detector that took a SensorManager could only ever be tuned by
 * standing in a room throwing a phone about.
 *
 * @param timestampNs the sensor event's own timestamp, not wall clock. Sensor timestamps
 *   come from a monotonic clock and are the only ones with the resolution this needs.
 * @param ax acceleration including gravity, m/s². TYPE_ACCELEROMETER, not
 *   TYPE_LINEAR_ACCELERATION: the latter is a fused, smoothed estimate and the smoothing
 *   removes exactly the sharp transient being looked for.
 * @param gx angular velocity, rad/s. Zero throughout if the device has no gyroscope.
 */
data class Sample(
    val timestampNs: Long,
    val ax: Float,
    val ay: Float,
    val az: Float,
    val gx: Float = 0f,
    val gy: Float = 0f,
    val gz: Float = 0f,
) {
    val accelMagnitude: Float get() = sqrt(ax * ax + ay * ay + az * az)

    val rotationMagnitude: Float get() = sqrt(gx * gx + gy * gy + gz * gz)

    companion object {
        /** Standard gravity, m/s². What accelMagnitude reads when the device is still. */
        const val GRAVITY = 9.80665f
    }
}
