# ProGuard / R8 rules for Reactor

# Keep Gson serialization models
-keepclassmembers class com.sfdex.reactor.data.model.** { <fields>; }
-keep class com.sfdex.reactor.data.model.** { *; }

# Keep ViewModel classes
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Compose standard keep rules
-dontwarn androidx.compose.**
