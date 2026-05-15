# =============================================================================
# Project ProGuard / R8 rules for the Compendium app.
#
# Hilt + Compose + SQLDelight ship their own consumer rules, so nothing extra
# is needed for them. The rules below cover kotlinx-serialization, which is
# used by both navigation-compose typed routes and the wire envelope. Without
# these, R8 strips the synthetic `Companion.serializer()` methods and the app
# crashes on the first navigation or contribution import/export.
# =============================================================================

# kotlinx-serialization — official keep rules
# https://github.com/Kotlin/kotlinx.serialization#android

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Domain model — persistence + dataloader round-trip these by name:
#   - AbilityListingDatabase serializes sealed-object types (AbilityType,
#     Property, Amount, Resource) via `KClass.simpleName`.
#   - StatusEffectDatabase looks up Property by `KClass.sealedSubclasses`.
#   - Several *Database readers use `Enum.valueOf` on Rank/Rarity strings
#     pulled from SQLite.
# R8 obfuscates simpleName by default and treats reflective Enum.valueOf as
# unreachable when the lookup string isn't a literal. Keeping the model
# package's class + member names preserves both round-trips.
-keepnames class wizardry.compendium.domain.model.** { *; }

# Drive backup module
-keep class wizardry.compendium.drive.backup.** { *; }

