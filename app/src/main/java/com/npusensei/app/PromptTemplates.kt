package com.npusensei.app

/**
 * Prompt templates that shape Gemma 4 E2B responses for NPU Sensei's
 * AR-guided hardware mentoring workflow.
 */
object PromptTemplates {

    const val SYSTEM_PROMPT =
        "You are NPU Sensei, an expert AR hardware mentor running on-device via " +
        "Gemma 4 E2B on a Snapdragon 8 Elite NPU. " +
        "You analyze photos of breadboards, Raspberry Pi GPIO headers, Arduino boards, " +
        "and electronic components. " +
        "When shown an image, identify visible components and their connections. " +
        "Provide step-by-step wiring guidance that is safe, concise, and beginner-friendly. " +
        "Always warn about polarity, voltage limits, and common mistakes."

    /** Wraps a user's free-form goal into a structured analysis request. */
    fun circuitAnalysis(userGoal: String): String =
        "The user wants to: $userGoal\n\n" +
        "Look at the camera image and:\n" +
        "1. Identify the visible components and their current connections.\n" +
        "2. List the next wiring step to achieve the user's goal.\n" +
        "3. Call out any safety concerns (shorts, wrong polarity, missing resistors).\n" +
        "Keep your answer under 120 words."

    /** Quick identification of a component in the frame. */
    fun identifyComponent(): String =
        "What electronic component is in the center of this image? " +
        "State its name, typical specs, and one sentence on how to wire it correctly."

    /** Validate wiring against a known task. */
    fun validateWiring(taskDescription: String): String =
        "The user is building: $taskDescription\n\n" +
        "Check the image for wiring errors. If everything looks correct, say so. " +
        "If not, describe what's wrong and how to fix it. Be concise."

    /** Text-only fallback when no image is available. */
    fun textOnlyGuide(question: String): String =
        "Answer this hardware/electronics question concisely:\n$question"
}
