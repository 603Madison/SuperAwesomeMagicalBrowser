package app.superawesomemagical.browser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/*
 * The two icons the small core Material set lacks, drawn in its rounded style (2dp strokes on a
 * 24dp grid). The extended set would add ~40 MB of code to the app for them.
 */

private fun glyph(name: String, block: ImageVector.Builder.() -> ImageVector.Builder): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).block().build()

private fun ImageVector.Builder.stroked(block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
    path(
        stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
    )

/** An eye with a line through it: private tabs. */
val PrivateGlyph: ImageVector = glyph("Private") {
    stroked {
        moveTo(2.5f, 12f)
        curveTo(5.2f, 7f, 18.8f, 7f, 21.5f, 12f)
        curveTo(18.8f, 17f, 5.2f, 17f, 2.5f, 12f)
        close()
    }
    stroked {
        moveTo(15f, 12f)
        arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 9f, y1 = 12f)
        arcTo(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 15f, y1 = 12f)
        close()
    }
    stroked {
        moveTo(4f, 4f)
        lineTo(20f, 20f)
    }
}

/** Two pages, one over the other: copy. */
val CopyGlyph: ImageVector = glyph("Copy") {
    stroked {
        moveTo(10f, 8f)
        horizontalLineTo(18f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 20f, y1 = 10f)
        verticalLineTo(19f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 18f, y1 = 21f)
        horizontalLineTo(10f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 8f, y1 = 19f)
        verticalLineTo(10f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 10f, y1 = 8f)
        close()
    }
    stroked {
        moveTo(16f, 4.5f)
        arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 14.5f, y1 = 3f)
        horizontalLineTo(6f)
        arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 4f, y1 = 5f)
        verticalLineTo(14.5f)
        arcTo(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 5.5f, y1 = 16f)
    }
}
