package io.github.sergioflatness.photoorderfix

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Simple line icons (24x24, 2px stroke) drawn for this app, so no icon library is needed. */
object LineIcons {
    val Back = line("back", "M19 12H5", "M11 6l-6 6 6 6")
    val Backup = line("backup", "M7 18a5 5 0 1 1 1-9.9A6 6 0 0 1 19 10a4 4 0 0 1-1 8z", "M12 16v-5", "M9.5 13.5L12 11l2.5 2.5")
    val Trash = line("trash", "M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13", "M12 11v5", "M10 13l2-2 2 2")
    val Info = line("info", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M12 11v6", "M12 7.5v0.5")
    val Shield = line("shield", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z", "M8.5 12l2.5 2.5 4.5-5")
    val Star = line("star", "M12 3l2.7 5.6 6.1 0.8-4.4 4.3 1 6.1L12 17l-5.4 2.8 1-6.1-4.4-4.3 6.1-0.8z")
    val Copies = line("copies", "M10 8h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z", "M16 5H6a1 1 0 0 0-1 1v10")
    val Pin = line("pin", "M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z", "M12 7a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z")
    val Check = line("check", "M5 12.5l4.5 4.5L19 7.5")

    private fun line(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach {
            b.addPath(
                addPathNodes(it),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }
}
