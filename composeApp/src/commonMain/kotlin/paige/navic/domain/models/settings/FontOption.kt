package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_custom_font
import navic.composeapp.generated.resources.option_google_sans_font
import navic.composeapp.generated.resources.option_system_font
import org.jetbrains.compose.resources.StringResource

enum class FontOption(val displayName: StringResource) {
	System(Res.string.option_system_font),
	GoogleSans(Res.string.option_google_sans_font),
	Custom(Res.string.option_custom_font)
}
