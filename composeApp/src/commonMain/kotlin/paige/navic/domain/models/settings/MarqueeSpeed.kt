package paige.navic.domain.models.settings

import navic.composeapp.generated.resources.Res
import navic.composeapp.generated.resources.option_marquee_disabled
import navic.composeapp.generated.resources.option_marquee_fast
import navic.composeapp.generated.resources.option_marquee_medium
import navic.composeapp.generated.resources.option_marquee_slow
import org.jetbrains.compose.resources.StringResource

enum class MarqueeSpeed(val value: Int, val displayName: StringResource) {
	Disabled(0, Res.string.option_marquee_disabled),
	Slow(6000, Res.string.option_marquee_slow),
	Medium(4000, Res.string.option_marquee_medium),
	Fast(1000, Res.string.option_marquee_fast)
}
