package wizardry.compendium.ui.theme

enum class ThemeMode {
    System,
    Light,
    Dark;

    val storedValue: String get() = name

    companion object {
        fun fromStoredValue(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: System
    }
}
