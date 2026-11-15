package com.freshkeep.app.domain

/** Bouwt de teksten van de meldingen op; puur en dus testbaar. */
object MeldingTekst {

    private const val MAX_NAMEN = 6

    data class Tekst(val titel: String, val inhoud: String)

    /**
     * Dagelijkse samenvatting, bv. "3 producten vervallen deze week: melk (morgen), yoghurt (2 dagen), kaas (5 dagen)".
     * @param items paren van productnaam en aantal dagen tot de vervaldatum.
     */
    fun samenvatting(items: List<Pair<String, Long>>, dagenVooraf: Int): Tekst? {
        if (items.isEmpty()) return null
        val gesorteerd = items.sortedBy { it.second }
        val periode = when (dagenVooraf) {
            1 -> "morgen"
            7 -> "deze week"
            else -> "binnen $dagenVooraf dagen"
        }
        val onderwerp = if (items.size == 1) "1 product vervalt" else "${items.size} producten vervallen"
        val lijst = gesorteerd.take(MAX_NAMEN)
            .joinToString(", ") { (naam, dagen) -> "$naam (${Vervalrekenaar.kortLabel(dagen)})" }
        val rest = if (items.size > MAX_NAMEN) " en ${items.size - MAX_NAMEN} andere" else ""
        return Tekst(
            titel = if (items.size == 1) "1 product vervalt binnenkort" else "${items.size} producten vervallen binnenkort",
            inhoud = "$onderwerp $periode: $lijst$rest",
        )
    }

    /** Melding op de dag zelf. */
    fun vandaag(namen: List<String>): Tekst? {
        if (namen.isEmpty()) return null
        if (namen.size == 1) return Tekst("Vervalt vandaag", "${namen.first()} vervalt vandaag. Eet smakelijk!")
        val lijst = namen.take(MAX_NAMEN).joinToString(", ")
        val rest = if (namen.size > MAX_NAMEN) " en ${namen.size - MAX_NAMEN} andere" else ""
        return Tekst("${namen.size} producten vervallen vandaag", "$lijst$rest")
    }
}
