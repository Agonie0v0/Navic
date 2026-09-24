package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_app_icon_default
import navic.composeapp.generated.resources.option_app_icon_inverted
import org.jetbrains.compose.resources.StringResource

enum class AppIconVariant(
	val activityName: String,
	val designer: String,
	val displayName: StringResource
) {
	Default("MainActivityDefault", designer = "ssalggnikool", displayName = Res.string.option_app_icon_default),
	Inverted("MainActivityInverted", designer = "ssalggnikool", displayName = Res.string.option_app_icon_inverted)
}
