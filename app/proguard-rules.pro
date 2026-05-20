# ════════════════════════════════════════════════════════════════════
#  OBD Pro — ProGuard Rules
# ════════════════════════════════════════════════════════════════════

# Keep Kotlin coroutines metadata
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep our model classes (used in StateFlow and serialization)
-keep class com.ramzi.obdpro.model.** { *; }

# Keep Bluetooth-related classes
-keep class android.bluetooth.** { *; }

# Keep Compose runtime
-dontwarn androidx.compose.**
