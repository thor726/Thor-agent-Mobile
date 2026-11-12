package com.thoragent.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/*
 * ThorIcons — pustaka ikon vektor 24dp MANDIRI (path data Material Symbols,
 * viewport 24x24). Tidak memakai material-icons-core sama sekali sehingga
 * ikon tidak hilang karena perubahan dependensi transitif.
 *
 * PENTING: setiap path() memberi fill = SolidColor(Color(0xFF000000)).
 * Pada Compose, PathComponent.draw() hanya menggambar bila fill != null
 * atau stroke != null — tanpa keduanya path TIDAK digambar sama sekali
 * (inilah penyebab ikon “hilang semua” di v0.2.0/v0.3.0). Tint ikon
 * (mis. emas Thor) diterapkan lewat Icon(imageVector, tint = ...)
 * yang memakai ColorFilter, bukan lewat fill di sini.
 *
 * Path data diambil dari kumpulan ikon resmi Material Design (Apache-2.0).
 */

object ThorIcons {
    private var _add: ImageVector? = null
    val Add: ImageVector
        get() = _add ?: ImageVector.Builder(
            name = "Add",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(19f, 13f)
                    lineTo(13f, 13f)
                    lineTo(13f, 19f)
                    lineTo(11f, 19f)
                    lineTo(11f, 13f)
                    lineTo(5f, 13f)
                    lineTo(5f, 11f)
                    lineTo(11f, 11f)
                    lineTo(11f, 5f)
                    lineTo(13f, 5f)
                    lineTo(13f, 11f)
                    lineTo(19f, 11f)
                    lineTo(19f, 13f)
                    close()
            }
        }.build().also { _add = it }

    private var _arrowBack: ImageVector? = null
    val ArrowBack: ImageVector
        get() = _arrowBack ?: ImageVector.Builder(
            name = "ArrowBack",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(20f, 11f)
                    lineTo(7.83f, 11f)
                    lineTo(13.42f, 5.41f)
                    lineTo(12f, 4f)
                    lineTo(4f, 12f)
                    lineTo(12f, 20f)
                    lineTo(13.41f, 18.59f)
                    lineTo(7.83f, 13f)
                    lineTo(20f, 13f)
                    lineTo(20f, 11f)
                    close()
            }
        }.build().also { _arrowBack = it }

    private var _arrowUpward: ImageVector? = null
    val ArrowUpward: ImageVector
        get() = _arrowUpward ?: ImageVector.Builder(
            name = "ArrowUpward",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(4f, 12f)
                    lineTo(5.41f, 13.41f)
                    lineTo(11f, 7.83f)
                    lineTo(11f, 20f)
                    lineTo(13f, 20f)
                    lineTo(13f, 7.83f)
                    lineTo(18.59f, 13.41f)
                    lineTo(20f, 12f)
                    lineTo(12f, 4f)
                    lineTo(4f, 12f)
                    close()
            }
        }.build().also { _arrowUpward = it }

    private var _attachFile: ImageVector? = null
    val AttachFile: ImageVector
        get() = _attachFile ?: ImageVector.Builder(
            name = "AttachFile",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(16.5f, 6f)
                    lineTo(16.5f, 17.5f)
                    curveTo(16.5f, 19.71f, 14.71f, 21.5f, 12.5f, 21.5f)
                    curveTo(10.29f, 21.5f, 8.5f, 19.71f, 8.5f, 17.5f)
                    lineTo(8.5f, 5f)
                    curveTo(8.5f, 3.62f, 9.62f, 2.5f, 11f, 2.5f)
                    curveTo(12.38f, 2.5f, 13.5f, 3.62f, 13.5f, 5f)
                    lineTo(13.5f, 15.5f)
                    curveTo(13.5f, 16.05f, 13.05f, 16.5f, 12.5f, 16.5f)
                    curveTo(11.95f, 16.5f, 11.5f, 16.05f, 11.5f, 15.5f)
                    lineTo(11.5f, 6f)
                    lineTo(10f, 6f)
                    lineTo(10f, 15.5f)
                    curveTo(10f, 16.88f, 11.12f, 18f, 12.5f, 18f)
                    curveTo(13.88f, 18f, 15f, 16.88f, 15f, 15.5f)
                    lineTo(15f, 5f)
                    curveTo(15f, 2.79f, 13.21f, 1f, 11f, 1f)
                    curveTo(8.79f, 1f, 7f, 2.79f, 7f, 5f)
                    lineTo(7f, 17.5f)
                    curveTo(7f, 20.54f, 9.46f, 23f, 12.5f, 23f)
                    curveTo(15.54f, 23f, 18f, 20.54f, 18f, 17.5f)
                    lineTo(18f, 6f)
                    lineTo(16.5f, 6f)
                    close()
            }
        }.build().also { _attachFile = it }

    private var _autoAwesome: ImageVector? = null
    val AutoAwesome: ImageVector
        get() = _autoAwesome ?: ImageVector.Builder(
            name = "AutoAwesome",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(19f, 9f)
                    lineTo(20.25f, 6.25f)
                    lineTo(23f, 5f)
                    lineTo(20.25f, 3.75f)
                    lineTo(19f, 1f)
                    lineTo(17.75f, 3.75f)
                    lineTo(15f, 5f)
                    lineTo(17.75f, 6.25f)
                    lineTo(19f, 9f)
                    close()
                    moveTo(11.5f, 9.5f)
                    lineTo(9f, 4f)
                    lineTo(6.5f, 9.5f)
                    lineTo(1f, 12f)
                    lineTo(6.5f, 14.5f)
                    lineTo(9f, 20f)
                    lineTo(11.5f, 14.5f)
                    lineTo(17f, 12f)
                    lineTo(11.5f, 10f)
                    close()
                    moveTo(19f, 15f)
                    lineTo(17.75f, 17.75f)
                    lineTo(15f, 19f)
                    lineTo(17.75f, 20.25f)
                    lineTo(19f, 23f)
                    lineTo(20.25f, 20.25f)
                    lineTo(23f, 19f)
                    lineTo(20.25f, 17.75f)
                    lineTo(19f, 15f)
                    close()
            }
        }.build().also { _autoAwesome = it }

    private var _bolt: ImageVector? = null
    val Bolt: ImageVector
        get() = _bolt ?: ImageVector.Builder(
            name = "Bolt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(11f, 21f)
                    lineTo(10f, 21f)
                    lineTo(11f, 14f)
                    lineTo(7.5f, 14f)
                    curveTo(6.92f, 14f, 6.93f, 13.68f, 7.12f, 13.34f)
                    curveTo(7.31f, 13f, 7.17f, 13.26f, 7.19f, 13.22f)
                    curveTo(8.48f, 10.94f, 10.42f, 7.54f, 13f, 3f)
                    lineTo(14f, 3f)
                    lineTo(13f, 10f)
                    lineTo(16.5f, 10f)
                    curveTo(16.99f, 10f, 17.06f, 10.33f, 16.97f, 10.51f)
                    lineTo(16.9f, 10.66f)
                    curveTo(12.96f, 17.55f, 11f, 21f, 11f, 21f)
                    close()
            }
        }.build().also { _bolt = it }

    private var _chat: ImageVector? = null
    val Chat: ImageVector
        get() = _chat ?: ImageVector.Builder(
            name = "Chat",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(20f, 2f)
                    lineTo(4f, 2f)
                    curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
                    lineTo(2f, 22f)
                    lineTo(6f, 18f)
                    lineTo(20f, 18f)
                    curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
                    lineTo(22f, 4f)
                    curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
                    close()
            }
        }.build().also { _chat = it }

    private var _check: ImageVector? = null
    val Check: ImageVector
        get() = _check ?: ImageVector.Builder(
            name = "Check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(9f, 16.17f)
                    lineTo(4.83f, 12f)
                    lineTo(3.41f, 13.41f)
                    lineTo(9f, 19f)
                    lineTo(21f, 7f)
                    lineTo(19.59f, 5.59f)
                    close()
            }
        }.build().also { _check = it }

    private var _checkCircle: ImageVector? = null
    val CheckCircle: ImageVector
        get() = _checkCircle ?: ImageVector.Builder(
            name = "CheckCircle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(12f, 2f)
                    curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                    curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
                    curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                    curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
                    close()
                    moveTo(10f, 17f)
                    lineTo(5f, 12f)
                    lineTo(6.41f, 10.59f)
                    lineTo(10f, 14.17f)
                    lineTo(17.59f, 6.58f)
                    lineTo(19f, 8f)
                    lineTo(10f, 17f)
                    close()
            }
        }.build().also { _checkCircle = it }

    private var _cancel: ImageVector? = null
    val Cancel: ImageVector
        get() = _cancel ?: ImageVector.Builder(
            name = "Cancel",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(12f, 2f)
                    curveTo(6.47f, 2f, 2f, 6.47f, 2f, 12f)
                    curveTo(2f, 17.53f, 6.47f, 22f, 12f, 22f)
                    curveTo(17.53f, 22f, 22f, 17.53f, 22f, 12f)
                    curveTo(22f, 6.47f, 17.53f, 2f, 12f, 2f)
                    close()
                    moveTo(17f, 15.59f)
                    lineTo(15.59f, 17f)
                    lineTo(12f, 13.41f)
                    lineTo(8.41f, 17f)
                    lineTo(7f, 15.59f)
                    lineTo(10.59f, 12f)
                    lineTo(7f, 8.41f)
                    lineTo(8.41f, 7f)
                    lineTo(12f, 10.59f)
                    lineTo(15.59f, 7f)
                    lineTo(17f, 8.41f)
                    lineTo(13.41f, 12f)
                    lineTo(17f, 15.59f)
                    close()
            }
        }.build().also { _cancel = it }

    private var _chevronRight: ImageVector? = null
    val ChevronRight: ImageVector
        get() = _chevronRight ?: ImageVector.Builder(
            name = "ChevronRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(10f, 6f)
                    lineTo(8.59f, 7.41f)
                    lineTo(13.17f, 12f)
                    lineTo(8.59f, 16.59f)
                    lineTo(10f, 18f)
                    lineTo(16f, 12f)
                    close()
            }
        }.build().also { _chevronRight = it }

    private var _close: ImageVector? = null
    val Close: ImageVector
        get() = _close ?: ImageVector.Builder(
            name = "Close",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(19f, 6.41f)
                    lineTo(17.59f, 5f)
                    lineTo(12f, 10.59f)
                    lineTo(6.41f, 5f)
                    lineTo(5f, 6.41f)
                    lineTo(10.59f, 12f)
                    lineTo(5f, 17.59f)
                    lineTo(6.41f, 19f)
                    lineTo(12f, 13.41f)
                    lineTo(17.59f, 19f)
                    lineTo(19f, 17.59f)
                    lineTo(13.41f, 12f)
                    close()
            }
        }.build().also { _close = it }

    private var _cloud: ImageVector? = null
    val Cloud: ImageVector
        get() = _cloud ?: ImageVector.Builder(
            name = "Cloud",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(19.35f, 10.04f)
                    curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
                    curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
                    curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
                    curveTo(0f, 17.31f, 2.69f, 20f, 6f, 20f)
                    lineTo(19f, 20f)
                    curveTo(21.76f, 20f, 24f, 17.76f, 24f, 15f)
                    curveTo(24f, 12.36f, 21.95f, 10.22f, 19.35f, 10.04f)
                    close()
            }
        }.build().also { _cloud = it }

    private var _code: ImageVector? = null
    val Code: ImageVector
        get() = _code ?: ImageVector.Builder(
            name = "Code",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(9.4f, 16.6f)
                    lineTo(4.8f, 12f)
                    lineTo(9.4f, 7.4f)
                    lineTo(8f, 6f)
                    lineTo(2f, 12f)
                    lineTo(8f, 18f)
                    lineTo(9.4f, 16.6f)
                    close()
                    moveTo(14.6f, 16.6f)
                    lineTo(19.2f, 12f)
                    lineTo(14.6f, 7.4f)
                    lineTo(16f, 6f)
                    lineTo(22f, 12f)
                    lineTo(16f, 18f)
                    lineTo(14.6f, 16.6f)
                    close()
            }
        }.build().also { _code = it }

    private var _contentCopy: ImageVector? = null
    val ContentCopy: ImageVector
        get() = _contentCopy ?: ImageVector.Builder(
            name = "ContentCopy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(16f, 1f)
                    lineTo(4f, 1f)
                    curveTo(2.9f, 1f, 2f, 1.9f, 2f, 3f)
                    lineTo(2f, 17f)
                    lineTo(4f, 17f)
                    lineTo(4f, 3f)
                    lineTo(16f, 3f)
                    lineTo(16f, 1f)
                    close()
                    moveTo(19f, 5f)
                    lineTo(8f, 5f)
                    curveTo(6.9f, 5f, 6f, 5.9f, 6f, 7f)
                    lineTo(6f, 21f)
                    curveTo(6f, 22.1f, 6.9f, 23f, 8f, 23f)
                    lineTo(19f, 23f)
                    curveTo(20.1f, 23f, 21f, 22.1f, 21f, 21f)
                    lineTo(21f, 7f)
                    curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
                    close()
                    moveTo(19f, 21f)
                    lineTo(8f, 21f)
                    lineTo(8f, 7f)
                    lineTo(19f, 7f)
                    lineTo(19f, 21f)
                    close()
            }
        }.build().also { _contentCopy = it }

    private var _delete: ImageVector? = null
    val Delete: ImageVector
        get() = _delete ?: ImageVector.Builder(
            name = "Delete",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(6f, 19f)
                    curveTo(6f, 20.1f, 6.9f, 21f, 8f, 21f)
                    lineTo(16f, 21f)
                    curveTo(17.1f, 21f, 18f, 20.1f, 18f, 19f)
                    lineTo(18f, 7f)
                    lineTo(6f, 7f)
                    lineTo(6f, 19f)
                    close()
                    moveTo(19f, 4f)
                    lineTo(15.5f, 4f)
                    lineTo(14.5f, 3f)
                    lineTo(9.5f, 3f)
                    lineTo(8.5f, 4f)
                    lineTo(5f, 4f)
                    lineTo(5f, 6f)
                    lineTo(19f, 6f)
                    lineTo(19f, 4f)
                    close()
            }
        }.build().also { _delete = it }

    private var _description: ImageVector? = null
    val Description: ImageVector
        get() = _description ?: ImageVector.Builder(
            name = "Description",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(14f, 2f)
                    lineTo(6f, 2f)
                    curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f)
                    lineTo(4f, 20f)
                    curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
                    lineTo(18f, 22f)
                    curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f)
                    lineTo(20f, 8f)
                    lineTo(14f, 2f)
                    close()
                    moveTo(16f, 18f)
                    lineTo(8f, 18f)
                    lineTo(8f, 16f)
                    lineTo(16f, 16f)
                    lineTo(16f, 18f)
                    close()
                    moveTo(16f, 14f)
                    lineTo(8f, 14f)
                    lineTo(8f, 12f)
                    lineTo(16f, 12f)
                    lineTo(16f, 14f)
                    close()
                    moveTo(13f, 9f)
                    lineTo(13f, 3.5f)
                    lineTo(18.5f, 9f)
                    lineTo(13f, 9f)
                    close()
            }
        }.build().also { _description = it }

    private var _download: ImageVector? = null
    val Download: ImageVector
        get() = _download ?: ImageVector.Builder(
            name = "Download",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(19f, 9f)
                    lineTo(15f, 9f)
                    lineTo(15f, 3f)
                    lineTo(9f, 3f)
                    lineTo(9f, 9f)
                    lineTo(5f, 9f)
                    lineTo(12f, 16f)
                    lineTo(19f, 9f)
                    close()
                    moveTo(5f, 18f)
                    lineTo(5f, 20f)
                    lineTo(19f, 20f)
                    lineTo(19f, 18f)
                    lineTo(5f, 18f)
                    close()
            }
        }.build().also { _download = it }

    private var _edit: ImageVector? = null
    val Edit: ImageVector
        get() = _edit ?: ImageVector.Builder(
            name = "Edit",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(3f, 17.25f)
                    lineTo(3f, 21f)
                    lineTo(6.75f, 21f)
                    lineTo(17.81f, 9.94f)
                    lineTo(14.06f, 6.19f)
                    lineTo(3f, 17.25f)
                    close()
                    moveTo(20.71f, 7.04f)
                    curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
                    lineTo(18.37f, 3.29f)
                    curveTo(17.98f, 2.9f, 17.35f, 2.9f, 16.96f, 3.29f)
                    lineTo(15.13f, 5.12f)
                    lineTo(18.88f, 8.87f)
                    lineTo(20.71f, 7.04f)
                    close()
            }
        }.build().also { _edit = it }

    private var _errorOutline: ImageVector? = null
    val ErrorOutline: ImageVector
        get() = _errorOutline ?: ImageVector.Builder(
            name = "ErrorOutline",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(11f, 15f)
                    lineTo(13f, 15f)
                    lineTo(13f, 17f)
                    lineTo(11f, 17f)
                    close()
                    moveTo(11f, 9f)
                    lineTo(13f, 9f)
                    lineTo(13f, 15f)
                    lineTo(11f, 15f)
                    close()
                    moveTo(11.99f, 10f)
                    curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
                    curveTo(2f, 17.52f, 6.47f, 22f, 11.99f, 22f)
                    curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                    curveTo(22f, 6.48f, 17.52f, 2f, 11.99f, 2f)
                    close()
                    moveTo(12f, 20f)
                    curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
                    curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
                    curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
                    curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
                    close()
            }
        }.build().also { _errorOutline = it }

    private var _expandLess: ImageVector? = null
    val ExpandLess: ImageVector
        get() = _expandLess ?: ImageVector.Builder(
            name = "ExpandLess",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(12f, 8f)
                    lineTo(6f, 14f)
                    lineTo(7.41f, 15.41f)
                    lineTo(12f, 10.83f)
                    lineTo(16.59f, 15.41f)
                    lineTo(18f, 14f)
                    close()
            }
        }.build().also { _expandLess = it }

    private var _expandMore: ImageVector? = null
    val ExpandMore: ImageVector
        get() = _expandMore ?: ImageVector.Builder(
            name = "ExpandMore",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(16.59f, 8.59f)
                    lineTo(12f, 13.17f)
                    lineTo(7.41f, 8.59f)
                    lineTo(6f, 10f)
                    lineTo(12f, 16f)
                    lineTo(18f, 10f)
                    close()
            }
        }.build().also { _expandMore = it }

    private var _folder: ImageVector? = null
    val Folder: ImageVector
        get() = _folder ?: ImageVector.Builder(
            name = "Folder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(10f, 4f)
                    lineTo(4f, 4f)
                    curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
                    lineTo(2f, 18f)
                    curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
                    lineTo(20f, 20f)
                    curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
                    lineTo(22f, 8f)
                    curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f)
                    lineTo(12f, 6f)
                    lineTo(10f, 4f)
                    close()
            }
        }.build().also { _folder = it }

    private var _folderOpen: ImageVector? = null
    val FolderOpen: ImageVector
        get() = _folderOpen ?: ImageVector.Builder(
            name = "FolderOpen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(20f, 6f)
                    lineTo(12f, 6f)
                    lineTo(10f, 4f)
                    lineTo(4f, 4f)
                    curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
                    lineTo(2f, 18f)
                    curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f)
                    lineTo(20f, 20f)
                    curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f)
                    lineTo(22f, 8f)
                    curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f)
                    close()
                    moveTo(20f, 18f)
                    lineTo(4f, 18f)
                    lineTo(4f, 8f)
                    lineTo(20f, 8f)
                    lineTo(20f, 18f)
                    close()
            }
        }.build().also { _folderOpen = it }

    private var _history: ImageVector? = null
    val History: ImageVector
        get() = _history ?: ImageVector.Builder(
            name = "History",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(13f, 3f)
                    curveTo(8.03f, 3f, 4f, 7.03f, 4f, 12f)
                    lineTo(1f, 12f)
                    lineTo(4.89f, 15.89f)
                    lineTo(4.96f, 16.03f)
                    lineTo(8.9f, 12f)
                    lineTo(6f, 12f)
                    curveTo(6f, 8.13f, 9.13f, 5f, 13f, 5f)
                    curveTo(16.87f, 5f, 20f, 8.13f, 20f, 12f)
                    curveTo(20f, 15.87f, 16.87f, 19f, 13f, 19f)
                    curveTo(11.07f, 19f, 9.32f, 18.21f, 8.06f, 16.94f)
                    lineTo(6.64f, 18.36f)
                    curveTo(8.27f, 19.99f, 10.51f, 21f, 13f, 21f)
                    curveTo(17.97f, 21f, 22f, 16.97f, 22f, 12f)
                    curveTo(22f, 7.03f, 17.97f, 3f, 13f, 3f)
                    close()
                    moveTo(12f, 8f)
                    lineTo(12f, 13f)
                    lineTo(16.28f, 15.54f)
                    lineTo(17f, 14.33f)
                    lineTo(13.5f, 12.25f)
                    lineTo(13.5f, 8f)
                    lineTo(11.5f, 8f)
                    close()
            }
        }.build().also { _history = it }

    private var _info: ImageVector? = null
    val Info: ImageVector
        get() = _info ?: ImageVector.Builder(
            name = "Info",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(11f, 7f)
                    lineTo(13f, 7f)
                    lineTo(13f, 9f)
                    lineTo(11f, 9f)
                    close()
                    moveTo(11f, 13f)
                    lineTo(13f, 13f)
                    lineTo(13f, 19f)
                    lineTo(11f, 19f)
                    close()
                    moveTo(12f, 10f)
                    curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                    curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
                    curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                    curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
                    close()
                    moveTo(12f, 20f)
                    curveTo(7.59f, 20f, 4f, 16.41f, 4f, 12f)
                    curveTo(4f, 7.59f, 7.59f, 4f, 12f, 4f)
                    curveTo(16.41f, 4f, 20f, 7.59f, 20f, 12f)
                    curveTo(20f, 16.41f, 16.41f, 20f, 12f, 20f)
                    close()
            }
        }.build().also { _info = it }

    private var _image: ImageVector? = null
    val Image: ImageVector
        get() = _image ?: ImageVector.Builder(
            name = "Image",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(21f, 19f)
                    lineTo(21f, 5f)
                    curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
                    lineTo(5f, 3f)
                    curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
                    lineTo(3f, 19f)
                    curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
                    lineTo(19f, 21f)
                    curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
                    close()
                    moveTo(8.5f, 13.5f)
                    lineTo(11f, 16.51f)
                    lineTo(14.5f, 12f)
                    lineTo(19f, 18f)
                    lineTo(5f, 18f)
                    lineTo(8.5f, 13.5f)
                    close()
            }
        }.build().also { _image = it }

    private var _volumeUp: ImageVector? = null
    val VolumeUp: ImageVector
        get() = _volumeUp ?: ImageVector.Builder(
            name = "VolumeUp",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(3f, 9f)
                    lineTo(3f, 15f)
                    lineTo(7f, 15f)
                    lineTo(12f, 20f)
                    lineTo(12f, 4f)
                    lineTo(7f, 9f)
                    lineTo(3f, 9f)
                    close()
                    moveTo(16.5f, 12f)
                    curveTo(16.5f, 10.23f, 15.48f, 8.71f, 14f, 7.97f)
                    lineTo(14f, 16.02f)
                    curveTo(15.48f, 15.29f, 16.5f, 13.77f, 16.5f, 12f)
                    close()
                    moveTo(14f, 3.23f)
                    lineTo(14f, 5.29f)
                    curveTo(16.89f, 6.15f, 19f, 8.83f, 19f, 12f)
                    curveTo(19f, 15.17f, 16.89f, 17.85f, 14f, 18.71f)
                    lineTo(14f, 20.77f)
                    curveTo(18.01f, 19.86f, 21f, 16.28f, 21f, 12f)
                    curveTo(21f, 7.72f, 18.01f, 4.14f, 14f, 3.23f)
                    close()
            }
        }.build().also { _volumeUp = it }

    private var _pets: ImageVector? = null
    val Pets: ImageVector
        get() = _pets ?: ImageVector.Builder(
            name = "Pets",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(4.5f, 9.5f)
                    moveTo(2f, 9.5f)
                    arcTo(2.5f, 2.5f, 0f, true, false, 7f, 9.5f)
                    arcTo(2.5f, 2.5f, 0f, true, false, 2f, 9.5f)
                    moveTo(9f, 5.5f)
                    moveTo(6.5f, 5.5f)
                    arcTo(2.5f, 2.5f, 0f, true, false, 11.5f, 5.5f)
                    arcTo(2.5f, 2.5f, 0f, true, false, 6.5f, 5.5f)
                    moveTo(15f, 5.5f)
                    moveTo(12.5f, 5.5f)
                    arcTo(2.5f, 2.5f, 0f, true, false, 17.5f, 5.5f)
                    arcTo(2.5f, 2.5f, 0f, true, false, 12.5f, 5.5f)
                    moveTo(19.5f, 9.5f)
                    moveTo(17f, 9.5f)
                    arcTo(2.5f, 2.5f, 0f, true, false, 22f, 9.5f)
                    arcTo(2.5f, 2.5f, 0f, true, false, 17f, 9.5f)
                    moveTo(17.34f, 14.86f)
                    curveTo(16.47f, 13.84f, 15.74f, 12.97f, 14.86f, 11.95f)
                    curveTo(14.4f, 11.41f, 13.81f, 10.87f, 13.11f, 10.63f)
                    curveTo(13f, 10.6f, 12.89f, 10.57f, 12.78f, 10.55f)
                    curveTo(12.53f, 10.51f, 12.26f, 10.5f, 11.99f, 10.5f)
                    curveTo(11.72f, 10.5f, 11.45f, 10.51f, 11.2f, 10.55f)
                    curveTo(11.09f, 10.57f, 10.98f, 10.6f, 10.87f, 10.63f)
                    curveTo(10.17f, 10.87f, 9.58f, 11.41f, 9.12f, 11.95f)
                    curveTo(8.24f, 12.97f, 7.51f, 13.84f, 6.64f, 14.86f)
                    curveTo(4.75f, 16.93f, 2.22f, 19.28f, 3.95f, 22.11f)
                    curveTo(5f, 23.83f, 7.34f, 24.32f, 9.09f, 23.85f)
                    curveTo(9.44f, 23.76f, 9.77f, 23.61f, 10.1f, 23.46f)
                    lineTo(13.88f, 23.46f)
                    curveTo(14.21f, 23.61f, 14.54f, 23.76f, 14.89f, 23.85f)
                    curveTo(16.64f, 24.32f, 18.98f, 23.83f, 20.03f, 22.11f)
                    curveTo(21.76f, 19.28f, 19.23f, 16.93f, 17.34f, 14.86f)
                    close()
            }
        }.build().also { _pets = it }

    private var _key: ImageVector? = null
    val Key: ImageVector
        get() = _key ?: ImageVector.Builder(
            name = "Key",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(12.65f, 10f)
                    curveTo(11.83f, 7.67f, 9.61f, 6f, 7f, 6f)
                    curveTo(3.69f, 6f, 1f, 8.69f, 1f, 12f)
                    curveTo(1f, 15.31f, 3.69f, 18f, 7f, 18f)
                    curveTo(9.61f, 18f, 11.83f, 16.33f, 12.65f, 14f)
                    lineTo(17f, 14f)
                    lineTo(17f, 18f)
                    lineTo(21f, 18f)
                    lineTo(21f, 14f)
                    lineTo(23f, 14f)
                    lineTo(23f, 10f)
                    lineTo(12.65f, 10f)
                    close()
                    moveTo(7f, 14f)
                    curveTo(5.9f, 14f, 5f, 13.1f, 5f, 12f)
                    curveTo(5f, 10.9f, 5.9f, 10f, 7f, 10f)
                    curveTo(8.1f, 10f, 9f, 10.9f, 9f, 12f)
                    curveTo(9f, 13.1f, 8.1f, 14f, 7f, 14f)
                    close()
            }
        }.build().also { _key = it }

    private var _lightbulb: ImageVector? = null
    val Lightbulb: ImageVector
        get() = _lightbulb ?: ImageVector.Builder(
            name = "Lightbulb",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(9f, 21f)
                    curveTo(9f, 21.55f, 9.45f, 22f, 10f, 22f)
                    lineTo(14f, 22f)
                    curveTo(14.55f, 22f, 15f, 21.55f, 15f, 21f)
                    lineTo(15f, 20f)
                    lineTo(9f, 20f)
                    lineTo(9f, 21f)
                    close()
                    moveTo(12f, 2f)
                    curveTo(8.14f, 2f, 5f, 5.14f, 5f, 9f)
                    curveTo(5f, 11.38f, 6.19f, 13.47f, 8f, 14.74f)
                    lineTo(8f, 17f)
                    curveTo(8f, 17.55f, 8.45f, 18f, 9f, 18f)
                    lineTo(15f, 18f)
                    curveTo(15.55f, 18f, 16f, 17.55f, 16f, 17f)
                    lineTo(16f, 14.74f)
                    curveTo(17.81f, 13.47f, 19f, 11.38f, 19f, 9f)
                    curveTo(19f, 5.14f, 15.86f, 2f, 12f, 2f)
                    close()
                    moveTo(14f, 13.7f)
                    lineTo(14f, 16f)
                    lineTo(10f, 16f)
                    lineTo(10f, 14.7f)
                    curveTo(8.06f, 13.75f, 7f, 11.99f, 7f, 9f)
                    curveTo(7f, 6.24f, 9.24f, 4f, 12f, 4f)
                    curveTo(14.76f, 4f, 17f, 6.24f, 17f, 9f)
                    curveTo(17f, 11.99f, 15.94f, 13.75f, 14f, 14.7f)
                    close()
            }
        }.build().also { _lightbulb = it }

    private var _link: ImageVector? = null
    val Link: ImageVector
        get() = _link ?: ImageVector.Builder(
            name = "Link",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(3.9f, 12f)
                    curveTo(3.9f, 10.29f, 5.29f, 8.9f, 7f, 8.9f)
                    lineTo(11f, 8.9f)
                    lineTo(11f, 7f)
                    lineTo(7f, 7f)
                    curveTo(4.24f, 7f, 2f, 9.24f, 2f, 12f)
                    curveTo(2f, 14.76f, 4.24f, 17f, 7f, 17f)
                    lineTo(11f, 17f)
                    lineTo(11f, 15.1f)
                    lineTo(7f, 15.1f)
                    curveTo(5.29f, 15.1f, 3.9f, 13.71f, 3.9f, 12f)
                    close()
                    moveTo(8f, 13f)
                    lineTo(16f, 13f)
                    lineTo(16f, 11f)
                    lineTo(8f, 11f)
                    lineTo(8f, 13f)
                    close()
                    moveTo(17f, 7f)
                    lineTo(13f, 7f)
                    lineTo(13f, 8.9f)
                    lineTo(17f, 8.9f)
                    curveTo(18.71f, 8.9f, 20.1f, 10.29f, 20.1f, 12f)
                    curveTo(20.1f, 13.71f, 18.71f, 15.1f, 17f, 15.1f)
                    lineTo(13f, 15.1f)
                    lineTo(13f, 17f)
                    lineTo(17f, 17f)
                    curveTo(19.76f, 17f, 22f, 14.76f, 22f, 12f)
                    curveTo(22f, 9.24f, 19.76f, 7f, 17f, 7f)
                    close()
            }
        }.build().also { _link = it }

    private var _memory: ImageVector? = null
    val Memory: ImageVector
        get() = _memory ?: ImageVector.Builder(
            name = "Memory",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(15f, 9f)
                    lineTo(9f, 9f)
                    lineTo(9f, 15f)
                    lineTo(15f, 15f)
                    lineTo(15f, 9f)
                    close()
                    moveTo(13f, 13f)
                    lineTo(11f, 13f)
                    lineTo(11f, 11f)
                    lineTo(13f, 11f)
                    lineTo(13f, 13f)
                    close()
                    moveTo(21f, 11f)
                    lineTo(21f, 9f)
                    lineTo(19f, 9f)
                    lineTo(19f, 7f)
                    curveTo(19f, 5.9f, 18.1f, 5f, 17f, 5f)
                    lineTo(15f, 5f)
                    lineTo(15f, 3f)
                    lineTo(13f, 3f)
                    lineTo(13f, 5f)
                    lineTo(11f, 5f)
                    lineTo(11f, 3f)
                    lineTo(9f, 3f)
                    lineTo(9f, 5f)
                    lineTo(7f, 5f)
                    curveTo(5.9f, 5f, 5f, 5.9f, 5f, 7f)
                    lineTo(5f, 9f)
                    lineTo(3f, 9f)
                    lineTo(3f, 11f)
                    lineTo(5f, 11f)
                    lineTo(5f, 13f)
                    lineTo(3f, 13f)
                    lineTo(3f, 15f)
                    lineTo(5f, 15f)
                    lineTo(5f, 17f)
                    curveTo(5f, 18.1f, 5.9f, 19f, 7f, 19f)
                    lineTo(9f, 19f)
                    lineTo(9f, 21f)
                    lineTo(11f, 21f)
                    lineTo(11f, 19f)
                    lineTo(13f, 19f)
                    lineTo(13f, 21f)
                    lineTo(15f, 21f)
                    lineTo(15f, 19f)
                    lineTo(17f, 19f)
                    curveTo(18.1f, 19f, 19f, 18.1f, 19f, 17f)
                    lineTo(19f, 15f)
                    lineTo(21f, 15f)
                    lineTo(21f, 13f)
                    lineTo(19f, 13f)
                    lineTo(19f, 11f)
                    lineTo(21f, 11f)
                    close()
                    moveTo(17f, 17f)
                    lineTo(7f, 17f)
                    lineTo(7f, 7f)
                    lineTo(17f, 7f)
                    lineTo(17f, 17f)
                    close()
            }
        }.build().also { _memory = it }

    private var _menuBook: ImageVector? = null
    val MenuBook: ImageVector
        get() = _menuBook ?: ImageVector.Builder(
            name = "MenuBook",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(21f, 5f)
                    curveTo(19.89f, 4.65f, 18.67f, 4.5f, 17.5f, 4.5f)
                    curveTo(15.55f, 4.5f, 13.45f, 4.9f, 12f, 6f)
                    curveTo(10.55f, 4.9f, 8.45f, 4.5f, 6.5f, 4.5f)
                    curveTo(4.55f, 4.5f, 2.45f, 4.9f, 1f, 6f)
                    lineTo(1f, 20.65f)
                    curveTo(1f, 20.9f, 1.25f, 21.15f, 1.5f, 21.15f)
                    curveTo(1.6f, 21.15f, 1.65f, 21.1f, 1.75f, 21.1f)
                    curveTo(3.1f, 20.45f, 5.05f, 20f, 6.5f, 20f)
                    curveTo(8.45f, 20f, 10.55f, 20.4f, 12f, 21.5f)
                    curveTo(13.35f, 20.65f, 15.8f, 20f, 17.5f, 20f)
                    curveTo(19.15f, 20f, 20.85f, 20.3f, 22.25f, 21.05f)
                    curveTo(22.35f, 21.1f, 22.4f, 21.1f, 22.5f, 21.1f)
                    curveTo(22.75f, 21.1f, 23f, 20.85f, 23f, 20.6f)
                    lineTo(23f, 6f)
                    curveTo(22.4f, 5.55f, 21.75f, 5.25f, 21f, 5f)
                    close()
                    moveTo(21f, 18.5f)
                    curveTo(19.9f, 18.15f, 18.7f, 18f, 17.5f, 18f)
                    curveTo(15.8f, 18f, 13.35f, 18.65f, 12f, 19.5f)
                    lineTo(12f, 8f)
                    curveTo(13.35f, 7.15f, 15.8f, 6.5f, 17.5f, 6.5f)
                    curveTo(18.7f, 6.5f, 19.9f, 6.65f, 21f, 7f)
                    lineTo(21f, 18.5f)
                    close()
            }
        }.build().also { _menuBook = it }

    private var _moreVert: ImageVector? = null
    val MoreVert: ImageVector
        get() = _moreVert ?: ImageVector.Builder(
            name = "MoreVert",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(12f, 8f)
                    curveTo(13.1f, 8f, 14f, 7.1f, 14f, 6f)
                    curveTo(14f, 4.9f, 13.1f, 4f, 12f, 4f)
                    curveTo(10.9f, 4f, 10f, 4.9f, 10f, 6f)
                    curveTo(10f, 7.1f, 10.9f, 8f, 12f, 8f)
                    close()
                    moveTo(12f, 10f)
                    curveTo(10.9f, 10f, 10f, 10.9f, 10f, 12f)
                    curveTo(10f, 13.1f, 10.9f, 14f, 12f, 14f)
                    curveTo(13.1f, 14f, 14f, 13.1f, 14f, 12f)
                    curveTo(14f, 10.9f, 13.1f, 10f, 12f, 10f)
                    close()
                    moveTo(12f, 16f)
                    curveTo(10.9f, 16f, 10f, 16.9f, 10f, 18f)
                    curveTo(10f, 19.1f, 10.9f, 20f, 12f, 20f)
                    curveTo(13.1f, 20f, 14f, 19.1f, 14f, 18f)
                    curveTo(14f, 16.9f, 13.1f, 16f, 12f, 16f)
                    close()
            }
        }.build().also { _moreVert = it }

    private var _person: ImageVector? = null
    val Person: ImageVector
        get() = _person ?: ImageVector.Builder(
            name = "Person",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(12f, 12f)
                    curveTo(14.21f, 12f, 16f, 10.21f, 16f, 8f)
                    curveTo(16f, 5.79f, 14.21f, 4f, 12f, 4f)
                    curveTo(9.79f, 4f, 8f, 5.79f, 8f, 8f)
                    curveTo(8f, 10.21f, 9.79f, 12f, 12f, 12f)
                    close()
                    moveTo(12f, 14f)
                    curveTo(9.33f, 14f, 4f, 15.34f, 4f, 18f)
                    lineTo(4f, 20f)
                    lineTo(20f, 20f)
                    lineTo(20f, 18f)
                    curveTo(20f, 15.33f, 14.67f, 14f, 12f, 14f)
                    close()
            }
        }.build().also { _person = it }

    private var _playArrow: ImageVector? = null
    val PlayArrow: ImageVector
        get() = _playArrow ?: ImageVector.Builder(
            name = "PlayArrow",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(8f, 5f)
                    lineTo(8f, 19f)
                    lineTo(19f, 12f)
                    close()
            }
        }.build().also { _playArrow = it }

    private var _refresh: ImageVector? = null
    val Refresh: ImageVector
        get() = _refresh ?: ImageVector.Builder(
            name = "Refresh",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(17.65f, 6.35f)
                    curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
                    curveTo(7.58f, 4f, 4.01f, 7.58f, 4.01f, 12f)
                    curveTo(4.01f, 16.42f, 7.58f, 20f, 12f, 20f)
                    curveTo(15.73f, 20f, 18.84f, 17.45f, 19.73f, 14f)
                    lineTo(17.65f, 14f)
                    curveTo(16.83f, 16.33f, 14.61f, 18f, 12f, 18f)
                    curveTo(8.69f, 18f, 6f, 15.31f, 6f, 12f)
                    curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
                    curveTo(13.66f, 6f, 15.14f, 6.69f, 16.22f, 7.78f)
                    lineTo(13f, 11f)
                    lineTo(20f, 11f)
                    lineTo(20f, 4f)
                    lineTo(17.65f, 6.35f)
                    close()
            }
        }.build().also { _refresh = it }

    private var _save: ImageVector? = null
    val Save: ImageVector
        get() = _save ?: ImageVector.Builder(
            name = "Save",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(17f, 3f)
                    lineTo(5f, 3f)
                    curveTo(3.89f, 3f, 3f, 3.9f, 3f, 5f)
                    lineTo(3f, 19f)
                    curveTo(3f, 20.1f, 3.89f, 21f, 5f, 21f)
                    lineTo(19f, 21f)
                    curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
                    lineTo(21f, 7f)
                    lineTo(17f, 3f)
                    close()
                    moveTo(12f, 19f)
                    curveTo(10.34f, 19f, 9f, 17.66f, 9f, 16f)
                    curveTo(9f, 14.34f, 10.34f, 13f, 12f, 13f)
                    curveTo(13.66f, 13f, 15f, 14.34f, 15f, 16f)
                    curveTo(15f, 17.66f, 13.66f, 19f, 12f, 19f)
                    close()
                    moveTo(15f, 9f)
                    lineTo(5f, 9f)
                    lineTo(5f, 5f)
                    lineTo(15f, 5f)
                    lineTo(15f, 9f)
                    close()
            }
        }.build().also { _save = it }

    private var _schedule: ImageVector? = null
    val Schedule: ImageVector
        get() = _schedule ?: ImageVector.Builder(
            name = "Schedule",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(11.99f, 2f)
                    curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
                    curveTo(2f, 17.52f, 6.47f, 22f, 11.99f, 22f)
                    curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                    curveTo(22f, 6.48f, 17.52f, 2f, 11.99f, 2f)
                    close()
                    moveTo(12f, 20f)
                    curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
                    curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
                    curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
                    curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
                    close()
                    moveTo(12.5f, 7f)
                    lineTo(11f, 7f)
                    lineTo(11f, 13f)
                    lineTo(16.25f, 16.15f)
                    lineTo(17f, 14.92f)
                    lineTo(12.5f, 12.25f)
                    close()
            }
        }.build().also { _schedule = it }

    private var _search: ImageVector? = null
    val Search: ImageVector
        get() = _search ?: ImageVector.Builder(
            name = "Search",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(15.5f, 14f)
                    lineTo(14.71f, 14f)
                    lineTo(14.43f, 13.73f)
                    curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
                    curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
                    curveTo(5.91f, 3f, 3f, 5.91f, 3f, 9.5f)
                    curveTo(3f, 13.09f, 5.91f, 16f, 9.5f, 16f)
                    curveTo(11.11f, 16f, 12.59f, 15.41f, 13.73f, 14.43f)
                    lineTo(14f, 14.71f)
                    lineTo(14f, 15.5f)
                    lineTo(19f, 20.49f)
                    lineTo(20.49f, 19f)
                    lineTo(15.5f, 14f)
                    close()
                    moveTo(9.5f, 14f)
                    curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
                    curveTo(5f, 7.01f, 7.01f, 5f, 9.5f, 5f)
                    curveTo(11.99f, 5f, 14f, 7.01f, 14f, 9.5f)
                    curveTo(14f, 11.99f, 11.99f, 14f, 9.5f, 14f)
                    close()
            }
        }.build().also { _search = it }

    private var _security: ImageVector? = null
    val Security: ImageVector
        get() = _security ?: ImageVector.Builder(
            name = "Security",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(12f, 1f)
                    lineTo(3f, 5f)
                    lineTo(3f, 11f)
                    curveTo(3f, 16.55f, 6.84f, 21.74f, 12f, 23f)
                    curveTo(17.16f, 21.74f, 21f, 16.55f, 21f, 11f)
                    lineTo(21f, 5f)
                    lineTo(12f, 1f)
                    close()
            }
        }.build().also { _security = it }

    private var _send: ImageVector? = null
    val Send: ImageVector
        get() = _send ?: ImageVector.Builder(
            name = "Send",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(2.01f, 21f)
                    lineTo(23f, 12f)
                    lineTo(2.01f, 3f)
                    lineTo(2f, 10f)
                    lineTo(17f, 12f)
                    lineTo(2f, 14f)
                    close()
            }
        }.build().also { _send = it }

    private var _settings: ImageVector? = null
    val Settings: ImageVector
        get() = _settings ?: ImageVector.Builder(
            name = "Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(19.14f, 12.94f)
                    curveTo(19.18f, 12.64f, 19.2f, 12.33f, 19.2f, 12f)
                    curveTo(19.2f, 11.68f, 19.18f, 11.36f, 19.13f, 11.06f)
                    lineTo(21.16f, 9.48f)
                    curveTo(21.34f, 9.34f, 21.39f, 9.07f, 21.28f, 8.87f)
                    lineTo(19.36f, 5.55f)
                    curveTo(19.24f, 5.33f, 18.99f, 5.26f, 18.77f, 5.33f)
                    lineTo(16.38f, 6.29f)
                    curveTo(15.88f, 5.91f, 15.35f, 5.59f, 14.76f, 5.35f)
                    lineTo(14.4f, 2.81f)
                    curveTo(14.36f, 2.57f, 14.16f, 2.4f, 13.92f, 2.4f)
                    lineTo(10.08f, 2.4f)
                    curveTo(9.84f, 2.4f, 9.65f, 2.57f, 9.61f, 2.81f)
                    lineTo(9.25f, 5.35f)
                    curveTo(8.66f, 5.59f, 8.12f, 5.92f, 7.63f, 6.29f)
                    lineTo(5.24f, 5.33f)
                    curveTo(5.02f, 5.25f, 4.77f, 5.33f, 4.65f, 5.55f)
                    lineTo(2.74f, 8.87f)
                    curveTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f)
                    lineTo(4.89f, 11.06f)
                    curveTo(4.84f, 11.36f, 4.8f, 11.69f, 4.8f, 12f)
                    curveTo(4.8f, 12.31f, 4.82f, 12.64f, 4.87f, 12.94f)
                    lineTo(2.84f, 14.52f)
                    curveTo(2.66f, 14.66f, 2.61f, 14.93f, 2.72f, 15.13f)
                    lineTo(4.64f, 18.45f)
                    curveTo(4.76f, 18.67f, 5.01f, 18.74f, 5.23f, 18.67f)
                    lineTo(7.62f, 17.71f)
                    curveTo(8.12f, 18.09f, 8.65f, 18.41f, 9.24f, 18.65f)
                    lineTo(9.6f, 21.19f)
                    curveTo(9.65f, 21.43f, 9.84f, 21.6f, 10.08f, 21.6f)
                    lineTo(13.92f, 21.6f)
                    curveTo(14.16f, 21.6f, 14.36f, 21.43f, 14.39f, 21.19f)
                    lineTo(14.75f, 18.65f)
                    curveTo(15.34f, 18.41f, 15.88f, 18.09f, 16.37f, 17.71f)
                    lineTo(18.76f, 18.67f)
                    curveTo(18.98f, 18.75f, 19.23f, 18.67f, 19.35f, 18.45f)
                    lineTo(21.27f, 15.13f)
                    curveTo(21.39f, 14.91f, 21.34f, 14.66f, 21.15f, 14.52f)
                    lineTo(19.14f, 12.94f)
                    close()
                    moveTo(12f, 15.6f)
                    curveTo(10.02f, 15.6f, 8.4f, 13.98f, 8.4f, 12f)
                    curveTo(8.4f, 10.02f, 10.02f, 8.4f, 12f, 8.4f)
                    curveTo(13.98f, 8.4f, 15.6f, 10.02f, 15.6f, 12f)
                    curveTo(15.6f, 13.98f, 13.98f, 15.6f, 12f, 15.6f)
                    close()
            }
        }.build().also { _settings = it }

    private var _share: ImageVector? = null
    val Share: ImageVector
        get() = _share ?: ImageVector.Builder(
            name = "Share",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(18f, 16.08f)
                    curveTo(17.24f, 16.08f, 16.56f, 16.38f, 16.04f, 16.85f)
                    lineTo(8.91f, 12.7f)
                    curveTo(8.96f, 12.47f, 9f, 12.24f, 9f, 12f)
                    curveTo(9f, 11.76f, 8.96f, 11.53f, 8.91f, 11.3f)
                    lineTo(15.96f, 7.19f)
                    curveTo(16.5f, 7.69f, 17.21f, 8f, 18f, 8f)
                    curveTo(19.66f, 8f, 21f, 6.66f, 21f, 5f)
                    curveTo(21f, 3.34f, 19.66f, 2f, 18f, 2f)
                    curveTo(16.34f, 2f, 15f, 3.34f, 15f, 5f)
                    curveTo(15f, 5.24f, 15.04f, 5.47f, 15.09f, 5.7f)
                    lineTo(8.04f, 9.81f)
                    curveTo(7.5f, 9.31f, 6.79f, 9f, 6f, 9f)
                    curveTo(4.34f, 9f, 3f, 10.34f, 3f, 12f)
                    curveTo(3f, 13.66f, 4.34f, 15f, 6f, 15f)
                    curveTo(6.79f, 15f, 7.5f, 14.69f, 8.04f, 14.19f)
                    lineTo(15.16f, 18.35f)
                    curveTo(15.11f, 18.56f, 15.08f, 18.78f, 15.08f, 19f)
                    curveTo(15.08f, 20.61f, 16.39f, 21.92f, 18f, 21.92f)
                    curveTo(19.61f, 21.92f, 20.92f, 20.61f, 20.92f, 19f)
                    curveTo(20.92f, 17.39f, 19.61f, 16.08f, 18f, 16.08f)
                    close()
            }
        }.build().also { _share = it }

    private var _smartToy: ImageVector? = null
    val SmartToy: ImageVector
        get() = _smartToy ?: ImageVector.Builder(
            name = "SmartToy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(20f, 9f)
                    lineTo(20f, 7f)
                    curveTo(20f, 5.9f, 19.1f, 5f, 18f, 5f)
                    lineTo(15f, 5f)
                    curveTo(15f, 3.34f, 13.66f, 2f, 12f, 2f)
                    curveTo(10.34f, 2f, 9f, 3.34f, 9f, 5f)
                    lineTo(6f, 5f)
                    curveTo(4.9f, 5f, 4f, 5.9f, 4f, 7f)
                    lineTo(4f, 9f)
                    curveTo(2.34f, 9f, 1f, 10.34f, 1f, 12f)
                    curveTo(1f, 13.66f, 2.34f, 15f, 4f, 15f)
                    lineTo(4f, 19f)
                    curveTo(4f, 20.1f, 4.9f, 21f, 6f, 21f)
                    lineTo(18f, 21f)
                    curveTo(19.1f, 21f, 20f, 20.1f, 20f, 19f)
                    lineTo(20f, 15f)
                    curveTo(21.66f, 15f, 23f, 13.66f, 23f, 12f)
                    curveTo(23f, 10.34f, 21.66f, 9f, 20f, 9f)
                    close()
                    moveTo(7.5f, 11.5f)
                    curveTo(6.67f, 11.5f, 6f, 10.83f, 6f, 10f)
                    curveTo(6f, 9.17f, 6.67f, 8.5f, 7.5f, 8.5f)
                    curveTo(8.33f, 8.5f, 9f, 9.17f, 9f, 10f)
                    curveTo(9f, 10.83f, 8.33f, 11.5f, 7.5f, 11.5f)
                    close()
                    moveTo(16.5f, 11.5f)
                    curveTo(15.67f, 11.5f, 15f, 10.83f, 15f, 10f)
                    curveTo(15f, 9.17f, 15.67f, 8.5f, 16.5f, 8.5f)
                    curveTo(17.33f, 8.5f, 18f, 9.17f, 18f, 10f)
                    curveTo(18f, 10.83f, 17.33f, 11.5f, 16.5f, 11.5f)
                    close()
            }
        }.build().also { _smartToy = it }

    private var _stop: ImageVector? = null
    val Stop: ImageVector
        get() = _stop ?: ImageVector.Builder(
            name = "Stop",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(6f, 6f)
                    lineTo(18f, 6f)
                    lineTo(18f, 18f)
                    lineTo(6f, 18f)
                    close()
            }
        }.build().also { _stop = it }

    private var _terminal: ImageVector? = null
    val Terminal: ImageVector
        get() = _terminal ?: ImageVector.Builder(
            name = "Terminal",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(2f, 4f)
                    lineTo(2f, 20f)
                    lineTo(22f, 20f)
                    lineTo(22f, 4f)
                    lineTo(2f, 4f)
                    close()
                    moveTo(20f, 18f)
                    lineTo(4f, 18f)
                    lineTo(4f, 8f)
                    lineTo(20f, 8f)
                    lineTo(20f, 18f)
                    close()
                    moveTo(6.81f, 8.6f)
                    lineTo(5.4f, 10f)
                    lineTo(8.9f, 13.5f)
                    lineTo(5.4f, 17f)
                    lineTo(6.81f, 18.41f)
                    lineTo(11.73f, 13.5f)
                    lineTo(6.81f, 8.6f)
                    close()
                    moveTo(13f, 15f)
                    lineTo(18f, 15f)
                    lineTo(18f, 17f)
                    lineTo(13f, 17f)
                    lineTo(13f, 15f)
                    close()
            }
        }.build().also { _terminal = it }

    private var _warning: ImageVector? = null
    val Warning: ImageVector
        get() = _warning ?: ImageVector.Builder(
            name = "Warning",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                // WAJIB: tanpa fill/stroke, PathComponent.draw() Compose TIDAK
                // menggambar apa pun -> ikon tampak hilang (bug v0.2.0-v0.3.0).
                fill = SolidColor(Color(0xFF000000)),
                stroke = null,
                strokeLineWidth = 1.0f
            ) {
                    moveTo(1f, 21f)
                    lineTo(23f, 21f)
                    lineTo(12f, 2f)
                    lineTo(1f, 21f)
                    close()
                    moveTo(13f, 18f)
                    lineTo(11f, 18f)
                    lineTo(11f, 16f)
                    lineTo(13f, 16f)
                    lineTo(13f, 18f)
                    close()
                    moveTo(13f, 14f)
                    lineTo(11f, 14f)
                    lineTo(11f, 10f)
                    lineTo(13f, 10f)
                    lineTo(13f, 14f)
                    close()
            }
        }.build().also { _warning = it }

}
