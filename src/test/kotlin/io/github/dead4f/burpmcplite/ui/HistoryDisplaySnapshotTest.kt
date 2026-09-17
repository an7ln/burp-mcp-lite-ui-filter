package io.github.dead4f.burpmcplite.ui

import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter
import kotlin.test.*

class HistoryDisplaySnapshotTest {
    private fun table() = JTable(DefaultTableModel(arrayOf(arrayOf<Any>(2, "example.com", "/noise"), arrayOf<Any>(9, "example.com", "/api")), arrayOf<Any>("#", "Host", "URL")))
    private fun tabs(table: JTable, title: String = "HTTP history") = JTabbedPane().apply {
        addTab(title, JScrollPane(table)); addTab("Other", JPanel()); selectedIndex = 1
    }

    @Test fun `reads filtered view not underlying model or selected tab`() = SwingUtilities.invokeAndWait {
        val table = table()
        val sorter = TableRowSorter(table.model)
        table.rowSorter = sorter
        val root = tabs(table, "HTTP 历史记录")
        sorter.rowFilter = RowFilter.regexFilter("/api", 2)
        assertEquals(setOf(9), HistoryDisplaySnapshot.read(listOf(root)))
        sorter.rowFilter = RowFilter.regexFilter("/noise", 2)
        assertEquals(setOf(2), HistoryDisplaySnapshot.read(listOf(root)))
        sorter.rowFilter = RowFilter.regexFilter("nothing", 2)
        assertEquals(emptySet(), HistoryDisplaySnapshot.read(listOf(root)))
        sorter.rowFilter = null
        table.moveColumn(0, 2)
        assertEquals(setOf(2, 9), HistoryDisplaySnapshot.read(listOf(root)))
    }

    @Test fun `ambiguous missing malformed or unrelated tables fail closed`() = SwingUtilities.invokeAndWait {
        assertFailsWith<IllegalStateException> { HistoryDisplaySnapshot.read(emptyList()) }
        assertFailsWith<IllegalStateException> { HistoryDisplaySnapshot.read(listOf(tabs(table()), tabs(table()))) }
        assertFailsWith<IllegalStateException> { HistoryDisplaySnapshot.read(listOf(tabs(table(), "Logger"))) }
        val bad = table().apply { setValueAt("invalid", 0, 0) }
        assertFailsWith<IllegalStateException> { HistoryDisplaySnapshot.read(listOf(tabs(bad))) }
    }
}
