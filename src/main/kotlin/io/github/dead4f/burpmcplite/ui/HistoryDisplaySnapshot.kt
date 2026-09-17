package io.github.dead4f.burpmcplite.ui

import java.awt.Component
import java.awt.Container
import java.awt.Window
import java.util.Locale
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.SwingUtilities

/** Best-effort Swing adapter, not a supported Montoya filter API. Never falls back to raw history. */
object HistoryDisplaySnapshot {
    fun ids(): Set<Int> {
        if (SwingUtilities.isEventDispatchThread()) return read(Window.getWindows().filter { it.isDisplayable })
        val task = FutureTask { read(Window.getWindows().filter { it.isDisplayable }) }
        SwingUtilities.invokeLater(task)
        return try {
            task.get(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            task.cancel(false)
            if (e is InterruptedException) Thread.currentThread().interrupt()
            throw IllegalStateException("HTTP history display snapshot failed. Open Proxy > HTTP history and retry. No raw-history fallback.", e)
        }
    }

    /** Must run on the EDT. Reads table VIEW rows (all filtered rows, not just viewport rows). */
    internal fun read(roots: List<Component>): Set<Int> {
        check(SwingUtilities.isEventDispatchThread()) { "Read Swing history on the EDT" }
        val candidates = roots.flatMap { descendants(it).toList() }.filterIsInstance<JTable>()
            .distinct().filter { table ->
                val columns = (0 until table.columnCount).map { normalize(table.getColumnName(it)) }
                "#" in columns && "url" in columns && columns.any { it in setOf("host", "主机") } && inHistoryTab(table)
            }
        check(candidates.size == 1) {
            "Expected one HTTP history table; found ${candidates.size}. Open the main Proxy > HTTP history tab, close duplicate history views, and retry. No raw-history fallback."
        }
        val table = candidates.single()
        val idColumn = (0 until table.columnCount).single { normalize(table.getColumnName(it)) == "#" }
        return (0 until table.rowCount).map { row ->
            val value = table.getValueAt(row, idColumn)
            value?.toString()?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
                ?: error("Unrecognized HTTP history ID at display row $row. No raw-history fallback.")
        }.toSet()
    }

    private fun normalize(s: String) = s.lowercase(Locale.ROOT).replace(Regex("\\s+"), "").replace("&", "")

    private fun inHistoryTab(table: JTable): Boolean {
        var child: Component = table
        while (true) {
            val parent = child.parent ?: return false
            if (parent is JTabbedPane) {
                val index = parent.indexOfComponent(child)
                if (index >= 0 && normalize(parent.getTitleAt(index)) in setOf("httphistory", "http历史记录", "http历史")) return true
            }
            child = parent
        }
    }

    private fun descendants(component: Component): Sequence<Component> = sequence {
        yield(component)
        if (component is Container) component.components.forEach { yieldAll(descendants(it)) }
    }
}
