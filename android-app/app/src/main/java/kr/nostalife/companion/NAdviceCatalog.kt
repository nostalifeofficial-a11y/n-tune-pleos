package kr.nostalife.companion

/** Accepted UI candidates, never commands to a vehicle. ESC is deliberately not an AI candidate. */
object NAdviceCatalog {
    private val three = listOf("Normal", "Sport", "Sport+")
    fun options(car: String, variant: String): Map<String, List<String>>? {
        val chassis = mapOf("suspension" to three, "steering" to three)
        if (car == "avante_n" && variant in listOf("DCT", "MT")) return chassis + mapOf(
            "engine" to three, (if (variant == "DCT") "transmission" else "rev") to three,
            "elsd" to listOf("Normal", "Sport"), "sound" to three)
        if (car !in listOf("ioniq5_n", "ioniq6_n") || variant != "EV") return null
        return chassis + mapOf("motor" to (listOf("Eco") + three), "elsd" to three,
            "sound" to listOf("Off", "Ignition", "Evolution", if (car == "ioniq5_n") "Supersonic" else "Lightspeed"),
            "eshift" to listOf("Off", "On")) + if (car == "ioniq6_n") mapOf(
                "ambient" to listOf("Off", "On"), "battery" to listOf("Off", "Drag", "Sprint", "Endurance")) else emptyMap()
    }
}
