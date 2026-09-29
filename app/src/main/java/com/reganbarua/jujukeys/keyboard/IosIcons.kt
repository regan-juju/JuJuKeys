package com.reganbarua.jujukeys.keyboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** iPhone-style shift arrows (the Material ones look different). */
object IosIcons {

    val shiftOff: ImageVector = shift(filled = false, capsLock = false)
    val shiftOn: ImageVector = shift(filled = true, capsLock = false)
    val capsLock: ImageVector = shift(filled = true, capsLock = true)

    private fun shift(filled: Boolean, capsLock: Boolean): ImageVector {
        val bottom = if (capsLock) 17.5f else 20f
        return ImageVector.Builder(
            name = "shift", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f
        ).apply {
            path(
                fill = if (filled) SolidColor(Color.White) else null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.7f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3.2f)
                lineTo(21f, 12.2f)
                lineTo(16.2f, 12.2f)
                lineTo(16.2f, bottom)
                lineTo(7.8f, bottom)
                lineTo(7.8f, 12.2f)
                lineTo(3f, 12.2f)
                close()
            }
            if (capsLock) {
                path(fill = SolidColor(Color.White)) {
                    moveTo(7.8f, 19.6f)
                    lineTo(16.2f, 19.6f)
                    lineTo(16.2f, 21.6f)
                    lineTo(7.8f, 21.6f)
                    close()
                }
            }
        }.build()
    }
}
