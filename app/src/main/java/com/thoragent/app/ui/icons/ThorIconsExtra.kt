package com.thoragent.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/*
 * Ikon tambahan v0.5.0 — sidebar ala Gemini & aksi cepat.
 * Mengikuti pola ThorIcons: path 24dp Material Symbols, fill eksplisit
 * (tanpa fill Compose tidak menggambar path — pelajaran v0.3.0).
 */
object ThorIconsExtra {

    private var _menu: ImageVector? = null
    /** Menu (hamburger) — buka sidebar. */
    val Menu: ImageVector
        get() = _menu ?: ImageVector.Builder(
            name = "Menu",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(3f, 6f)
                lineTo(21f, 6f)
                lineTo(21f, 8f)
                lineTo(3f, 8f)
                lineTo(3f, 6f)
                close()
                moveTo(3f, 11f)
                lineTo(21f, 11f)
                lineTo(21f, 13f)
                lineTo(3f, 13f)
                lineTo(3f, 11f)
                close()
                moveTo(3f, 16f)
                lineTo(21f, 16f)
                lineTo(21f, 18f)
                lineTo(3f, 18f)
                lineTo(3f, 16f)
                close()
            }
        }.build().also { _menu = it }

    private var _editNote: ImageVector? = null
    /** EditNote — tombol "Chat baru" ala Gemini. */
    val EditNote: ImageVector
        get() = _editNote ?: ImageVector.Builder(
            name = "EditNote",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(3f, 5f)
                lineTo(13f, 5f)
                lineTo(13f, 7f)
                lineTo(3f, 7f)
                lineTo(3f, 5f)
                close()
                moveTo(3f, 9f)
                lineTo(13f, 9f)
                lineTo(13f, 11f)
                lineTo(3f, 11f)
                lineTo(3f, 9f)
                close()
                moveTo(3f, 13f)
                lineTo(9f, 13f)
                lineTo(9f, 15f)
                lineTo(3f, 15f)
                lineTo(3f, 13f)
                close()
                moveTo(14.1f, 8.5f)
                lineTo(15.5f, 7.1f)
                lineTo(21f, 12.6f)
                lineTo(19.6f, 14f)
                lineTo(14.1f, 8.5f)
                close()
                moveTo(12.8f, 12.9f)
                lineTo(14.2f, 11.5f)
                lineTo(15.6f, 12.9f)
                lineTo(14.2f, 14.3f)
                lineTo(12.8f, 12.9f)
                close()
                moveTo(16.1f, 12.9f)
                lineTo(17.5f, 11.5f)
                lineTo(21f, 15f)
                lineTo(21f, 19f)
                curveTo(21f, 19.55f, 20.55f, 20f, 20f, 20f)
                lineTo(16f, 20f)
                lineTo(12.2f, 16.2f)
                lineTo(13.6f, 14.8f)
                lineTo(16f, 17.2f)
                lineTo(19.6f, 13.6f)
                lineTo(18.2f, 12.2f)
                lineTo(16.1f, 14.3f)
                lineTo(14.7f, 12.9f)
                close()
            }
        }.build().also { _editNote = it }

    private var _tune: ImageVector? = null
    /** Tune — pengaturan (model, skin, personality) di sidebar. */
    val Tune: ImageVector
        get() = _tune ?: ImageVector.Builder(
            name = "Tune",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(3f, 6f)
                lineTo(3f, 4f)
                lineTo(9f, 4f)
                lineTo(9f, 6f)
                lineTo(3f, 6f)
                close()
                moveTo(11f, 6f)
                lineTo(11f, 4f)
                lineTo(21f, 4f)
                lineTo(21f, 6f)
                lineTo(11f, 6f)
                close()
                moveTo(5f, 11f)
                lineTo(5f, 9f)
                lineTo(3f, 9f)
                lineTo(3f, 7f)
                lineTo(9f, 7f)
                lineTo(9f, 9f)
                lineTo(7f, 9f)
                lineTo(7f, 11f)
                lineTo(5f, 11f)
                close()
                moveTo(13f, 11f)
                lineTo(13f, 9f)
                lineTo(11f, 9f)
                lineTo(11f, 7f)
                lineTo(21f, 7f)
                lineTo(21f, 9f)
                lineTo(19f, 9f)
                lineTo(19f, 11f)
                lineTo(17f, 11f)
                lineTo(17f, 9f)
                lineTo(15f, 9f)
                lineTo(15f, 11f)
                lineTo(13f, 11f)
                close()
                moveTo(3f, 16f)
                lineTo(3f, 14f)
                lineTo(13f, 14f)
                lineTo(13f, 16f)
                lineTo(3f, 16f)
                close()
                moveTo(15f, 16f)
                lineTo(15f, 14f)
                lineTo(21f, 14f)
                lineTo(21f, 16f)
                lineTo(15f, 16f)
                close()
                moveTo(15f, 20f)
                lineTo(15f, 18f)
                lineTo(17f, 18f)
                lineTo(17f, 16f)
                lineTo(19f, 16f)
                lineTo(19f, 18f)
                lineTo(21f, 18f)
                lineTo(21f, 20f)
                lineTo(15f, 20f)
                close()
                moveTo(3f, 20f)
                lineTo(3f, 18f)
                lineTo(9f, 18f)
                lineTo(9f, 20f)
                lineTo(3f, 20f)
                close()
            }
        }.build().also { _tune = it }

    private var _switchAccess: ImageVector? = null
    /** SwitchAccount — ganti provider/model dari sidebar. */
    val SwitchAccess: ImageVector
        get() = _switchAccess ?: ImageVector.Builder(
            name = "SwitchAccount",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(12f, 2f)
                curveTo(8.6f, 2f, 6f, 4.6f, 6f, 8f)
                curveTo(6f, 9.2f, 6.3f, 10.3f, 6.9f, 11.3f)
                curveTo(6.3f, 11.8f, 6f, 12.5f, 6f, 13.3f)
                curveTo(6f, 14.2f, 6.4f, 15f, 7f, 15.6f)
                lineTo(7f, 16f)
                curveTo(7f, 18.2f, 9.2f, 20f, 12f, 20f)
                curveTo(14.8f, 20f, 17f, 18.2f, 17f, 16f)
                lineTo(17f, 15.6f)
                curveTo(17.6f, 15f, 18f, 14.2f, 18f, 13.3f)
                curveTo(18f, 12.5f, 17.7f, 11.8f, 17.1f, 11.3f)
                curveTo(17.7f, 10.3f, 18f, 9.2f, 18f, 8f)
                curveTo(18f, 4.6f, 15.4f, 2f, 12f, 2f)
                close()
                moveTo(12f, 4f)
                curveTo(14.3f, 4f, 16f, 5.7f, 16f, 8f)
                curveTo(16f, 10.3f, 14.3f, 12f, 12f, 12f)
                curveTo(9.7f, 12f, 8f, 10.3f, 8f, 8f)
                curveTo(8f, 5.7f, 9.7f, 4f, 12f, 4f)
                close()
                moveTo(12f, 14f)
                curveTo(13.3f, 14f, 14.5f, 14.4f, 15.3f, 15f)
                lineTo(15.3f, 16f)
                curveTo(15.3f, 17.1f, 13.8f, 18f, 12f, 18f)
                curveTo(10.2f, 18f, 8.7f, 17.1f, 8.7f, 16f)
                lineTo(8.7f, 15f)
                curveTo(9.5f, 14.4f, 10.7f, 14f, 12f, 14f)
                close()
                moveTo(20f, 6f)
                lineTo(20f, 10f)
                lineTo(22f, 8f)
                lineTo(20f, 6f)
                close()
                moveTo(20f, 14f)
                lineTo(20f, 18f)
                lineTo(22f, 16f)
                lineTo(20f, 14f)
                close()
                moveTo(4f, 6f)
                lineTo(4f, 10f)
                lineTo(2f, 8f)
                lineTo(4f, 6f)
                close()
                moveTo(4f, 14f)
                lineTo(4f, 18f)
                lineTo(2f, 16f)
                lineTo(4f, 14f)
                close()
            }
        }.build().also { _switchAccess = it }
}
