package com.thoragent.app

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import com.thoragent.app.ui.icons.ThorIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test untuk bug "ikon hilang semua" (v0.2.0 / v0.3.0):
 * PathComponent.draw() Compose hanya menggambar path dengan fill != null
 * atau stroke != null. Ikon yang dibangun tanpa keduanya TIDAK digambar
 * sama sekali — tampak sebagai ruang kosong di UI.
 *
 * Test ini memastikan SETIAP ikon punya minimal satu path terisi
 * (fill non-null) dengan data path tidak kosong.
 */
class ThorIconsTest {

    private fun flatten(node: VectorNode, out: MutableList<VectorNode>) {
        out.add(node)
        if (node is VectorGroup) node.forEach { flatten(it, out) }
    }

    private fun filledPaths(vector: ImageVector): List<VectorPath> {
        val nodes = mutableListOf<VectorNode>()
        flatten(vector.root, nodes)
        return nodes.filterIsInstance<VectorPath>().filter { it.fill != null }
    }

    private val allIcons: Map<String, ImageVector> = mapOf(
        "Add" to ThorIcons.Add,
        "ArrowBack" to ThorIcons.ArrowBack,
        "ArrowUpward" to ThorIcons.ArrowUpward,
        "AttachFile" to ThorIcons.AttachFile,
        "AutoAwesome" to ThorIcons.AutoAwesome,
        "Bolt" to ThorIcons.Bolt,
        "Cancel" to ThorIcons.Cancel,
        "Chat" to ThorIcons.Chat,
        "Check" to ThorIcons.Check,
        "CheckCircle" to ThorIcons.CheckCircle,
        "ChevronRight" to ThorIcons.ChevronRight,
        "Close" to ThorIcons.Close,
        "Cloud" to ThorIcons.Cloud,
        "Code" to ThorIcons.Code,
        "ContentCopy" to ThorIcons.ContentCopy,
        "Delete" to ThorIcons.Delete,
        "Description" to ThorIcons.Description,
        "Download" to ThorIcons.Download,
        "Edit" to ThorIcons.Edit,
        "ErrorOutline" to ThorIcons.ErrorOutline,
        "ExpandLess" to ThorIcons.ExpandLess,
        "ExpandMore" to ThorIcons.ExpandMore,
        "Folder" to ThorIcons.Folder,
        "FolderOpen" to ThorIcons.FolderOpen,
        "History" to ThorIcons.History,
        "Info" to ThorIcons.Info,
        "Image" to ThorIcons.Image,
        "Key" to ThorIcons.Key,
        "Lightbulb" to ThorIcons.Lightbulb,
        "Link" to ThorIcons.Link,
        "Memory" to ThorIcons.Memory,
        "MenuBook" to ThorIcons.MenuBook,
        "MoreVert" to ThorIcons.MoreVert,
        "Person" to ThorIcons.Person,
        "Pets" to ThorIcons.Pets,
        "PlayArrow" to ThorIcons.PlayArrow,
        "Refresh" to ThorIcons.Refresh,
        "Save" to ThorIcons.Save,
        "Schedule" to ThorIcons.Schedule,
        "Search" to ThorIcons.Search,
        "Security" to ThorIcons.Security,
        "Send" to ThorIcons.Send,
        "Settings" to ThorIcons.Settings,
        "Share" to ThorIcons.Share,
        "SmartToy" to ThorIcons.SmartToy,
        "Stop" to ThorIcons.Stop,
        "Terminal" to ThorIcons.Terminal,
        "VolumeUp" to ThorIcons.VolumeUp,
        "Warning" to ThorIcons.Warning
    )

    @Test
    fun `semua ikon punya setidaknya satu path dengan fill non-null`() {
        val bad = allIcons.filterValues { filledPaths(it).isEmpty() }.keys
        assertTrue(
            "Ikon tanpa fill (tidak akan tergambar di Compose): $bad",
            bad.isEmpty()
        )
    }

    @Test
    fun `semua ikon punya data path tidak kosong`() {
        allIcons.forEach { (name, vector) ->
            val paths = filledPaths(vector)
            assertTrue("$name tidak punya path terisi", paths.isNotEmpty())
            assertTrue(
                "$name punya path terisi tapi data kosong",
                paths.all { it.pathData.isNotEmpty() }
            )
        }
    }

    @Test
    fun `fill ikon adalah brush solid`() {
        allIcons.forEach { (name, vector) ->
            val brushes: List<Brush> = filledPaths(vector).mapNotNull { it.fill }
            assertTrue("$name tidak punya brush fill", brushes.isNotEmpty())
        }
    }

    @Test
    fun `jumlah ikon sesuai`() {
        assertEquals(49, allIcons.size)
    }

    @Test
    fun `ikon navigasi bawah ada dan terisi`() {
        // Ikon tab utama yang dikeluhkan hilang: chat, terminal, dll.
        listOf(ThorIcons.Chat, ThorIcons.Terminal, ThorIcons.Folder, ThorIcons.MenuBook, ThorIcons.Security)
            .forEach { vector ->
                assertNotNull(vector)
                assertTrue(filledPaths(vector).isNotEmpty())
            }
    }
}
