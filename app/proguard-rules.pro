# ===== Thor Agent Mobile — aturan R8 (release minified, ditandatangani debug key) =====

# libsu (shell root on-demand)
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# kotlinx.serialization — keep serializer yang digenerate & field @Serializable
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class ** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.thoragent.app.**$$serializer { *; }
-keepclassmembers class com.thoragent.app.** { *** Companion; }
-keepclasseswithmembers class com.thoragent.app.** { kotlinx.serialization.KSerializer serializer(...); }
-dontnote kotlinx.serialization.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# Compose — aturan consumer sudah dibawa AAR; jaga enum & fungsi @Composable yang direfleksi
-keepclassmembers enum com.thoragent.app.** { *; }

# Room & DataStore sudah membawa consumer rules sendiri.
