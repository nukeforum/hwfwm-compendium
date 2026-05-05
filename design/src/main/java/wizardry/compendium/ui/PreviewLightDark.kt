package wizardry.compendium.ui

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

/**
 * Multipreview annotation that renders a composable in both light and
 * dark UI modes.
 *
 * Use in place of `@Preview(showBackground = true)`. Each preview should
 * still wrap its content in `CompendiumTheme(...)` so the dark variant
 * actually picks up the dark color scheme.
 */
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class PreviewLightDark
