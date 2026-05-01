package com.npusensei.app

data class CircuitStep(
    val id: Int,
    val instruction: String,
    val detail: String,
    val verificationPrompt: String,
    val safetyWarning: String? = null,
)

object CircuitProjects {

    val LED_BASIC = listOf(
        CircuitStep(
            id = 1,
            instruction = "Place the breadboard",
            detail = "Set the breadboard on a flat surface with the power rails visible on the sides.",
            verificationPrompt = "Do you see a breadboard in this image? Answer only YES or NO, then one short sentence explaining what you see.",
        ),
        CircuitStep(
            id = 2,
            instruction = "Insert the resistor",
            detail = "Place a 330Ω resistor so it bridges across several rows on the breadboard.",
            verificationPrompt = "Is there a resistor inserted into the breadboard? Answer only YES or NO, then one short sentence describing its placement.",
        ),
        CircuitStep(
            id = 3,
            instruction = "Insert the LED",
            detail = "Place the LED with its longer leg (anode) in the same row as one end of the resistor, and the shorter leg (cathode) in the next row.",
            verificationPrompt = "Is there an LED inserted into the breadboard near the resistor? Answer only YES or NO, then one short sentence describing its placement.",
            safetyWarning = "Long leg = anode (+). Short leg = cathode (−). Getting this backwards will prevent the LED from lighting.",
        ),
        CircuitStep(
            id = 4,
            instruction = "Wire power to the resistor",
            detail = "Connect a jumper wire from the positive (+) power rail to the row with the free end of the resistor.",
            verificationPrompt = "Is there a jumper wire connecting the power rail to the resistor's row on the breadboard? Answer only YES or NO, then one short sentence describing what you see.",
        ),
        CircuitStep(
            id = 5,
            instruction = "Wire ground to the LED",
            detail = "Connect a jumper wire from the LED's cathode row to the ground (−) rail.",
            verificationPrompt = "Is there a jumper wire connecting the LED's cathode side to the ground rail? Answer only YES or NO, then one short sentence describing what you see.",
        ),
        CircuitStep(
            id = 6,
            instruction = "Connect power supply",
            detail = "Connect your battery or USB power source: positive to the + rail, ground to the − rail. The LED should light up!",
            verificationPrompt = "Does it look like a power source (battery pack, USB cable, or power supply) is connected to the breadboard rails? Answer only YES or NO, then one short sentence.",
            safetyWarning = "Double-check polarity before connecting power. Wrong polarity can damage the LED.",
        ),
    )
}
