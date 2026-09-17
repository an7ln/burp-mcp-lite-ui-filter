package io.github.dead4f.burpmcplite.snapshot

import kotlin.test.*
import io.github.dead4f.burpmcplite.tools.ListHistoryTool
import io.github.dead4f.burpmcplite.tools.ListHistoryArgs

class DisplayFilteredHistorySourceTest {
    private fun raw(path: String) = RawEntry("GET $path HTTP/1.1\r\nHost: example.com\r\n\r\n", "HTTP/1.1 200 OK\r\n\r\n")

    @Test fun `live membership and rollback preserve native IDs`() {
        val raw = FakeHistorySource(mutableListOf(2 to raw("/udcc/collect.html"), 6 to raw("/noise.png"), 9 to raw("/api/detail")))
        var ids = setOf(9)
        var enabled = true
        val source = DisplayFilteredHistorySource(raw, { ids }, { enabled })
        assertEquals(listOf(9), source.entries().map { it.id }.toList())
        assertEquals(1, source.size())
        assertNull(source.byId(6))
        val listing = ListHistoryTool.run(source, ListHistoryArgs())
        assertTrue("/api/detail" in listing)
        assertFalse("noise.png" in listing)
        ids = setOf(6)
        assertEquals(listOf(6), source.entries().map { it.id }.toList())
        assertNull(source.byId(9))
        ids = emptySet()
        assertEquals(0, source.size())
        enabled = false
        assertEquals(listOf(2, 6, 9), source.entries().map { it.id }.toList())
        assertNotNull(source.byId(6))
        println("BEHAVIOR: raw=[2,6,9]; display=[9]; changed=[6]; empty=[]; rollback=[2,6,9]")
    }

    @Test fun `snapshot error never exposes raw history`() {
        val source = DisplayFilteredHistorySource(FakeHistorySource.of(raw("/noise.png")), { error("missing table") })
        assertFailsWith<IllegalStateException> { source.entries().toList() }
        assertFailsWith<IllegalStateException> { source.byId(0) }
        assertFailsWith<IllegalStateException> { source.size() }
    }
}
