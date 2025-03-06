/*
* Converted using https://composables.com/svgtocompose
*/

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.joebad.fastbreak.ui.theme.LocalColors

public val MenuDeep: ImageVector
	@Composable
	get() {
		val colors = LocalColors.current
		if (_MenuDeep != null) {
			return _MenuDeep!!
		}
		_MenuDeep = ImageVector.Builder(
			name = "MenuDeep",
			defaultWidth = 24.dp,
			defaultHeight = 24.dp,
			viewportWidth = 24f,
			viewportHeight = 24f
		).apply {
			path(
				fill = null,
				fillAlpha = 1.0f,
//            stroke = null,
				strokeAlpha = 1.0f,
				strokeLineWidth = 10f,
				strokeLineCap = StrokeCap.Round,
				strokeLineJoin = StrokeJoin.Round,
				strokeLineMiter = 1.0f,
				pathFillType = PathFillType.NonZero
			) {
				moveTo(0f, 0f)
				horizontalLineToRelative(24f)
				verticalLineToRelative(24f)
				horizontalLineTo(0f)
				close()
			}
			path(
				fill = null,
				fillAlpha = 1.0f,
				stroke = SolidColor(colors.onPrimary),
				strokeAlpha = 1.0f,
				strokeLineWidth = 2f,
				strokeLineCap = StrokeCap.Round,
				strokeLineJoin = StrokeJoin.Round,
				strokeLineMiter = 1.0f,
				pathFillType = PathFillType.NonZero
			) {
				moveTo(4f, 6f)
				horizontalLineToRelative(16f)
			}
			path(
				fill = null,
				fillAlpha = 1.0f,
				stroke = SolidColor(colors.onPrimary),
				strokeAlpha = 1.0f,
				strokeLineWidth = 2f,
				strokeLineCap = StrokeCap.Round,
				strokeLineJoin = StrokeJoin.Round,
				strokeLineMiter = 1.0f,
				pathFillType = PathFillType.NonZero
			) {
				moveTo(7f, 12f)
				horizontalLineToRelative(13f)
			}
			path(
				fill = null,
				fillAlpha = 1.0f,
				stroke = SolidColor(colors.onPrimary),
				strokeAlpha = 1.0f,
				strokeLineWidth = 2f,
				strokeLineCap = StrokeCap.Round,
				strokeLineJoin = StrokeJoin.Round,
				strokeLineMiter = 1.0f,
				pathFillType = PathFillType.NonZero
			) {
				moveTo(10f, 18f)
				horizontalLineToRelative(10f)
			}
		}.build()
		return _MenuDeep!!
	}

private var _MenuDeep: ImageVector? = null