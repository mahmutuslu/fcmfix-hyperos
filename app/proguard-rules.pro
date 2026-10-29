# Xposed sınıflarını koru
-keep class io.github.fcmfix.hyperos.MainHook
-keep class io.github.fcmfix.hyperos.hook.** { *; }
-keepattributes *Annotation*

# Xposed API
-dontwarn de.robv.android.xposed.**
-keep class de.robv.android.xposed.** { *; }
