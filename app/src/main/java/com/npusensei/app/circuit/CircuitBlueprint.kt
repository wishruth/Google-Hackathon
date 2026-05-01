package com.npusensei.app.circuit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CircuitBlueprint(
    val id: String,
    val title: String,
    val summary: String = "",
    @SerialName("estimated_minutes") val estimatedMinutes: Int = 0,
    val difficulty: String = "beginner",
    @SerialName("bill_of_materials") val billOfMaterials: List<BomItem> = emptyList(),
    val steps: List<BlueprintStep> = emptyList(),
    @SerialName("breadboard_geometry") val breadboardGeometry: BreadboardGeometry? = null,
)

@Serializable
data class BomItem(
    val id: String,
    val label: String,
    val qty: Int = 1,
    @SerialName("value_ohms") val valueOhms: Int? = null,
)

@Serializable
data class BlueprintStep(
    val n: Int,
    val instruction: String,
    @SerialName("requires_visible") val requiresVisible: List<String> = emptyList(),
    val highlight: Highlight? = null,
    @SerialName("expected_resistor_ohms") val expectedResistorOhms: Int? = null,
    @SerialName("tolerance_pct") val tolerancePct: Int = 10,
    val verify: String? = null,
)

@Serializable
data class Highlight(
    val type: String,
    val target: String? = null,
    val row: Int? = null,
    val col: String? = null,
    val label: String? = null,
)

@Serializable
data class BreadboardGeometry(
    val rows: Int = 30,
    val cols: List<String> = listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"),
    @SerialName("power_rails") val powerRails: List<String> = listOf("+", "-"),
)
