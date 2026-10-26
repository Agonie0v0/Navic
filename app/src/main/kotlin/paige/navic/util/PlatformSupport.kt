package paige.navic.util

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

object PlatformSupport {
	@ChecksSdkIntAtLeast(Build.VERSION_CODES.S)
	val SUPPORTS_DYNAMIC_THEME: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
	@ChecksSdkIntAtLeast(Build.VERSION_CODES.Q)
	val SUPPORTS_DARK_THEME: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
}
