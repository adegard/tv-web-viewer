package com.example.adblock

data class AdBlockEvent(
    val type: Type,
    val url: String
) {
    enum class Type {
        REQUEST,
        SCRIPT,
        POPUP,
        DOM,
        OTHER;

        companion object {
            fun from(value: String?): Type = when (value?.uppercase()) {
                "REQUEST" -> REQUEST
                "SCRIPT" -> SCRIPT
                "POPUP" -> POPUP
                "DOM" -> DOM
                else -> OTHER
            }
        }
    }
}