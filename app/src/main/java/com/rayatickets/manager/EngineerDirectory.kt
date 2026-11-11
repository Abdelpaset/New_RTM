package com.rayatickets.manager

object EngineerDirectory {
    // Phone numbers formatted for WhatsApp (country code 20 without + or spaces)
    private val directory = mapOf(
        "mohammed othman" to "201153099020",
        "ahmed mohammed" to "201003766197",
        "ahmed mohammed abdelaal" to "201003766197",
        "mohamad gamal" to "201011816098",
        "mohammed gamal" to "201011816098",
        "mahmoud mamdouh" to "201068609833",
        "mohammed fareed" to "201558399998",
        "ali taher" to "201507300036",
        "walid salah" to "201275571014",
        "mahmoud bakry" to "201022882823",
        "mohammed ashraf" to "201124990986",
        "mina ehab" to "201025966444"
    )

    private fun normalize(name: String): String {
        return name.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
    }

    /**
     * Finds the engineer phone number using either their two-part name or full raw name.
     */
    fun getPhone(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val normalized = normalize(name)
        // 1. Direct match
        directory[normalized]?.let { return it }

        // 2. Try two-part cleaned name
        val twoPart = TicketParser.cleanAssigneeName(name)
        val normalizedTwoPart = normalize(twoPart)
        directory[normalizedTwoPart]?.let { return it }

        // 3. Prefix / Contains match
        for ((key, phone) in directory) {
            if (normalized.startsWith(key) || key.startsWith(normalized)) {
                return phone
            }
        }
        return null
    }
}
