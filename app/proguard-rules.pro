# R8 rules for JuJuKeys.
# Google ML Kit Translate loads parts of itself by name (reflection / native code). Its AARs ship
# their own rules, but we do not rely on them alone: shrinking a class it needs would only show up
# as a crash when the user opens Translate in a release build.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_translate.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_language_id_common.** { *; }
-keep class com.google.android.gms.common.internal.** { *; }
-keepclassmembers class * { @com.google.android.gms.common.annotation.KeepForSdk *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# Keyboard: started by Android by name from the manifest (kept automatically); the settings JSON
# uses org.json from the platform (nothing to keep).
