package io.github.dead4f.burpmcplite.snapshot

/** Applies one fresh UI membership snapshot per call, before parsing message bodies. */
class DisplayFilteredHistorySource(
    private val delegate: HistorySource,
    private val visibleIds: () -> Set<Int>,
    private val enabled: () -> Boolean = { true },
) : HistorySource {
    override fun entries(): Sequence<HistoryEntry> {
        if (!enabled()) return delegate.entries()
        val ids = visibleIds()
        return delegate.entries(ids)
    }

    override fun byId(id: Int): HistoryEntry? {
        if (enabled() && id !in visibleIds()) return null
        return delegate.byId(id)
    }

    override fun size(): Int = entries().count()
}
