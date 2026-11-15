package com.freshkeep.app.domain.model

/** Waar een product bewaard wordt. */
enum class Locatie(val label: String) {
    KOELKAST("Koelkast"),
    DIEPVRIES("Diepvries"),
    KAST("Kast"),
    ANDERE("Andere");

    companion object {
        fun fromName(name: String?): Locatie = entries.firstOrNull { it.name == name } ?: KOELKAST
    }
}

/** Soort datum op de verpakking. */
enum class DatumType(val label: String, val uitleg: String) {
    THT("THT", "Ten minste houdbaar tot: vaak nog goed na deze datum. Kijk, ruik, proef."),
    TGT("TGT", "Te gebruiken tot: na deze datum niet meer opeten."),
    ONBEKEND("Onbekend", "Het type datum is niet gekend.");

    companion object {
        fun fromApi(value: String?): DatumType = when (value?.trim()?.uppercase()) {
            "THT" -> THT
            "TGT" -> TGT
            else -> ONBEKEND
        }
    }
}

/** Wat er met een product gebeurd is. */
enum class Resultaat { OP, WEGGEGOOID }

/** Thema-instelling van de app. */
enum class Thema(val label: String) {
    AUTOMATISCH("Automatisch"),
    LICHT("Licht"),
    DONKER("Donker"),
}
