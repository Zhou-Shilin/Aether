package com.zhousl.aether.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Sidebar uses Lucide outlines, vendored locally to avoid forcing a toolchain upgrade.
object LucideIcons {
    val Search: ImageVector
        get() {
            if (_search != null) return _search!!

            _search = ImageVector.Builder(
                name = "search",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(21f, 21f)
                    lineToRelative(-4.34f, -4.34f)
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(19f, 11f)
                    arcTo(8f, 8f, 0f, false, true, 11f, 19f)
                    arcTo(8f, 8f, 0f, false, true, 3f, 11f)
                    arcTo(8f, 8f, 0f, false, true, 19f, 11f)
                    close()
                }
            }.build()

            return _search!!
        }

    val Settings: ImageVector
        get() {
            if (_settings != null) return _settings!!

            _settings = ImageVector.Builder(
                name = "settings",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(9.671f, 4.136f)
                    arcToRelative(2.34f, 2.34f, 0f, false, true, 4.659f, 0f)
                    arcToRelative(2.34f, 2.34f, 0f, false, false, 3.319f, 1.915f)
                    arcToRelative(2.34f, 2.34f, 0f, true, true, 2.33f, 4.033f)
                    arcToRelative(2.34f, 2.34f, 0f, false, false, 0f, 3.831f)
                    arcToRelative(2.34f, 2.34f, 0f, true, true, -2.33f, 4.033f)
                    arcToRelative(2.34f, 2.34f, 0f, false, false, -3.319f, 1.915f)
                    arcToRelative(2.34f, 2.34f, 0f, false, true, -4.659f, 0f)
                    arcToRelative(2.34f, 2.34f, 0f, false, false, -3.32f, -1.915f)
                    arcToRelative(2.34f, 2.34f, 0f, true, true, -2.33f, -4.033f)
                    arcToRelative(2.34f, 2.34f, 0f, false, false, 0f, -3.831f)
                    arcTo(2.34f, 2.34f, 0f, true, true, 6.35f, 6.051f)
                    arcToRelative(2.34f, 2.34f, 0f, false, false, 3.319f, -1.915f)
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(15f, 12f)
                    arcTo(3f, 3f, 0f, false, true, 12f, 15f)
                    arcTo(3f, 3f, 0f, false, true, 9f, 12f)
                    arcTo(3f, 3f, 0f, false, true, 15f, 12f)
                    close()
                }
            }.build()

            return _settings!!
        }

    val SquarePen: ImageVector
        get() {
            if (_squarePen != null) return _squarePen!!

            _squarePen = ImageVector.Builder(
                name = "square-pen",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(12f, 3f)
                    horizontalLineTo(5f)
                    arcToRelative(2f, 2f, 0f, false, false, -2f, 2f)
                    verticalLineToRelative(14f)
                    arcToRelative(2f, 2f, 0f, false, false, 2f, 2f)
                    horizontalLineToRelative(14f)
                    arcToRelative(2f, 2f, 0f, false, false, 2f, -2f)
                    verticalLineToRelative(-7f)
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(18.375f, 2.625f)
                    arcToRelative(1f, 1f, 0f, false, true, 3f, 3f)
                    lineToRelative(-9.013f, 9.014f)
                    arcToRelative(2f, 2f, 0f, false, true, -0.853f, 0.505f)
                    lineToRelative(-2.873f, 0.84f)
                    arcToRelative(0.5f, 0.5f, 0f, false, true, -0.62f, -0.62f)
                    lineToRelative(0.84f, -2.873f)
                    arcToRelative(2f, 2f, 0f, false, true, 0.506f, -0.852f)
                    close()
                }
            }.build()

            return _squarePen!!
        }

    val X: ImageVector
        get() {
            if (_x != null) return _x!!

            _x = ImageVector.Builder(
                name = "x",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(18f, 6f)
                    lineTo(6f, 18f)
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(6f, 6f)
                    lineToRelative(12f, 12f)
                }
            }.build()

            return _x!!
        }

    val Copy: ImageVector
        get() {
            if (_copy != null) return _copy!!

            _copy = ImageVector.Builder(
                name = "copy",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(9f, 9f)
                    horizontalLineToRelative(11f)
                    arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                    verticalLineToRelative(9f)
                    arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                    horizontalLineTo(9f)
                    arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                    verticalLineToRelative(-9f)
                    arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                    close()
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(5f, 15f)
                    horizontalLineTo(4f)
                    arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                    verticalLineTo(4f)
                    arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                    horizontalLineToRelative(9f)
                    arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                    verticalLineToRelative(1f)
                }
            }.build()

            return _copy!!
        }

    val Cursor: ImageVector
        get() {
            if (_cursor != null) return _cursor!!

            _cursor = ImageVector.Builder(
                name = "cursor",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(3f, 3f)
                    lineToRelative(7.07f, 16.97f)
                    lineToRelative(2.51f, -7.39f)
                    lineToRelative(7.39f, -2.51f)
                    close()
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(13f, 13f)
                    lineToRelative(6f, 6f)
                }
            }.build()

            return _cursor!!
        }

    val MousePointer2: ImageVector
        get() {
            if (_mousePointer2 != null) return _mousePointer2!!

            _mousePointer2 = ImageVector.Builder(
                name = "mouse-pointer-2",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(4.037f, 4.688f)
                    arcToRelative(0.495f, 0.495f, 0f, false, true, 0.651f, -0.651f)
                    lineToRelative(16f, 6.5f)
                    arcToRelative(0.5f, 0.5f, 0f, false, true, -0.063f, 0.947f)
                    lineToRelative(-6.124f, 1.58f)
                    arcToRelative(2f, 2f, 0f, false, false, -1.438f, 1.435f)
                    lineToRelative(-1.579f, 6.126f)
                    arcToRelative(0.5f, 0.5f, 0f, false, true, -0.947f, 0.063f)
                    close()
                }
            }.build()

            return _mousePointer2!!
        }

    val RotateCcw: ImageVector
        get() {
            if (_rotateCcw != null) return _rotateCcw!!

            _rotateCcw = ImageVector.Builder(
                name = "rotate-ccw",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(3f, 12f)
                    arcToRelative(9f, 9f, 0f, true, false, 9f, -9f)
                    arcToRelative(9.75f, 9.75f, 0f, false, false, -6.74f, 2.74f)
                    lineTo(3f, 8f)
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(3f, 3f)
                    verticalLineToRelative(5f)
                    horizontalLineToRelative(5f)
                }
            }.build()

            return _rotateCcw!!
        }

    val Trash2: ImageVector
        get() {
            if (_trash2 != null) return _trash2!!

            _trash2 = ImageVector.Builder(
                name = "trash-2",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(3f, 6f)
                    horizontalLineToRelative(18f)
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(8f, 6f)
                    verticalLineTo(4f)
                    arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
                    horizontalLineToRelative(4f)
                    arcToRelative(2f, 2f, 0f, false, true, 2f, 2f)
                    verticalLineToRelative(2f)
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(19f, 6f)
                    lineToRelative(-1f, 14f)
                    arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
                    horizontalLineTo(8f)
                    arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
                    lineTo(5f, 6f)
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(10f, 11f)
                    verticalLineToRelative(6f)
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(14f, 11f)
                    verticalLineToRelative(6f)
                }
            }.build()

            return _trash2!!
        }

    val ChartNoAxesColumn: ImageVector
        get() {
            if (_chartNoAxesColumn != null) return _chartNoAxesColumn!!

            _chartNoAxesColumn = ImageVector.Builder(
                name = "chart-no-axes-column",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(5f, 21f)
                    verticalLineToRelative(-6f)
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(12f, 21f)
                    verticalLineTo(9f)
                }
                path(
                    fill = SolidColor(Color.Transparent),
                    stroke = SolidColor(Color(0xFF000000)),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                ) {
                    moveTo(19f, 21f)
                    verticalLineTo(3f)
                }
            }.build()

            return _chartNoAxesColumn!!
        }

    private var _search: ImageVector? = null
    private var _settings: ImageVector? = null
    private var _squarePen: ImageVector? = null
    private var _x: ImageVector? = null
    private var _copy: ImageVector? = null
    private var _cursor: ImageVector? = null
    private var _mousePointer2: ImageVector? = null
    private var _rotateCcw: ImageVector? = null
    private var _trash2: ImageVector? = null
    private var _chartNoAxesColumn: ImageVector? = null
}
