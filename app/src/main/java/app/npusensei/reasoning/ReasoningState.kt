package app.npusensei.reasoning

/** High-level status of the Gemma reasoning engine. */
enum class EngineStatus {
    IDLE,
    LOADING,
    READY,
    INFERRING,
    ERROR,
}

/**
 * Immutable snapshot of everything the UI needs to render the
 * Gemma reasoning overlay on the camera screen.
 */
data class ReasoningState(
    val engineStatus: EngineStatus = EngineStatus.IDLE,
    val activeBackend: String = "–",
    val initTimeMs: Long = 0,
    val modelName: String = "",

    /** Streaming response accumulated so far. */
    val responseText: String = "",
    /** True while tokens are still arriving. */
    val isStreaming: Boolean = false,
    /** Non-null when something went wrong. */
    val errorMessage: String? = null,
)
