package com.intent.screentime.data.intent

/**
 * The reason vocabulary behind the prompt's chips.
 *
 * The chip text is both what the user reads and what the ledger shows as its row label —
 * one vocabulary rather than a display name and a separate ledger name that can drift
 * apart. The key exists so a row keeps its identity if the wording is ever changed, and so
 * rows written before the ledger existed can still be grouped.
 */
object Reasons {
    const val CONNECTION = "connection"
    const val CHECK = "check"
    const val DRIFT = "drift"
    const val BOREDOM = "boredom"

    data class Option(val key: String, val label: String)

    val OPTIONS: List<Option> = listOf(
        Option(CONNECTION, "Replying to someone"),
        Option(CHECK, "Checking something"),
        Option(DRIFT, "Scrolling"),
        Option(BOREDOM, "Killing time"),
    )

    /**
     * The two reasons that describe an open nobody actually intended.
     *
     * The other two are legitimate uses of a phone, and counting them here would make the
     * week-over-week figure say that answering a message is a failure.
     */
    val mindlessKeys: Set<String> = setOf(DRIFT, BOREDOM)

    /** Resolves a label to its key, for rows written before the key column existed. */
    fun keyForLabel(label: String): String? = OPTIONS.firstOrNull { it.label == label }?.key

    fun labelFor(key: String?): String? = OPTIONS.firstOrNull { it.key == key }?.label

    fun optionFor(key: String?): Option? = OPTIONS.firstOrNull { it.key == key }
}
