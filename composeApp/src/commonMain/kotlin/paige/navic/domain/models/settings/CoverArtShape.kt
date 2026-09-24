package paige.navic.domain.models.settings

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle
import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_shape_circle
import navic.composeapp.generated.resources.option_shape_curved
import navic.composeapp.generated.resources.option_shape_soft
import navic.composeapp.generated.resources.option_shape_square
import org.jetbrains.compose.resources.StringResource

enum class CoverArtShape(
	val displayName: StringResource,
	val shape: Shape,
	val decreasedShape: Shape = shape
) {
	Square(Res.string.option_shape_square, RectangleShape),
	Soft(Res.string.option_shape_soft, ContinuousRoundedRectangle(10.dp), ContinuousRoundedRectangle(8.dp)),
	Curved(Res.string.option_shape_curved, ContinuousRoundedRectangle(32.dp), ContinuousRoundedRectangle(10.dp)),
	Circle(Res.string.option_shape_circle, CircleShape)
}
