package com.npusensei.app.ml

import android.graphics.RectF

data class Detection(
    val classId: Int,
    val label: String,
    val score: Float,
    val box: RectF,
    val resistorOhms: Int? = null,
)

data class SceneState(
    val detections: List<Detection>,
    val frameWidthPx: Int,
    val frameHeightPx: Int,
    val timestampNs: Long,
) {
    fun has(label: String) = detections.any { it.label == label }
    fun first(label: String): Detection? = detections.firstOrNull { it.label == label }
    fun all(label: String): List<Detection> = detections.filter { it.label == label }

    companion object {
        val EMPTY = SceneState(emptyList(), 0, 0, 0L)
    }
}

object CircuitClasses {
    const val GPIO_BREAKOUT = "gpio_breakout"
    const val COMPLETED_CIRCUIT = "completed_circuit"
    const val BLUE_CABLE = "blue_cable"
    const val BREADBOARD = "breadboard"
    const val RED_LED = "red_led"
    const val RASPBERRY_PI = "raspberry_pi"
    const val RED_WIRE = "red_wire"
    const val RESISTOR = "resistor"
    const val RIBBON_CABLE = "ribbon_cable"

    /**
     * Order MUST match the `names:` list in dataset/data.yaml exported by
     * Roboflow. As of the current dataset:
     *   0: 40-gpio-t-type-breakdown   -> GPIO_BREAKOUT
     *   1: Completed Circuit          -> COMPLETED_CIRCUIT
     *   2: blue cable                 -> BLUE_CABLE
     *   3: breadboard                 -> BREADBOARD
     *   4: led light                  -> RED_LED
     *   5: raspberry pi               -> RASPBERRY_PI
     *   6: red wire                   -> RED_WIRE
     *   7: resistor                   -> RESISTOR
     *   8: ribbon cable               -> RIBBON_CABLE
     */
    val ORDERED = listOf(
        GPIO_BREAKOUT,
        COMPLETED_CIRCUIT,
        BLUE_CABLE,
        BREADBOARD,
        RED_LED,
        RASPBERRY_PI,
        RED_WIRE,
        RESISTOR,
        RIBBON_CABLE,
    )
}
