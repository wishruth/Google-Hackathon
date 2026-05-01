package com.npusensei.app.circuit

import com.npusensei.app.ml.CircuitClasses
import com.npusensei.app.ml.SceneState
import kotlin.math.abs

class StepEngine {

    fun evaluate(step: BlueprintStep, scene: SceneState): StepStatus {
        val missing = step.requiresVisible.filterNot { scene.has(it) }
        if (missing.isNotEmpty()) return StepStatus.WaitingFor(missing)

        step.expectedResistorOhms?.let { expected ->
            val resistor = scene.first(CircuitClasses.RESISTOR)
            val actual = resistor?.resistorOhms
            if (actual != null) {
                val tolerance = expected * step.tolerancePct / 100
                if (abs(actual - expected) > tolerance) {
                    return StepStatus.WrongValue(expected = expected, actual = actual)
                }
            }
        }

        return StepStatus.Ready
    }

    fun shouldAdvance(previous: StepStatus, readyForMs: Long): Boolean =
        previous == StepStatus.Ready && readyForMs >= READY_HOLD_MS

    companion object {
        const val READY_HOLD_MS = 2_000L
    }
}

sealed interface StepStatus {
    data class WaitingFor(val missing: List<String>) : StepStatus
    data class Misplaced(val component: String, val reason: String) : StepStatus
    data class WrongValue(val expected: Int, val actual: Int) : StepStatus
    data object Ready : StepStatus
    data object Complete : StepStatus
}
