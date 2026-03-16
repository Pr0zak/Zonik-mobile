# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Media3
-keep class androidx.media3.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Google Cast
-keep class com.zonik.app.media.CastOptionsProvider { *; }
-keep class com.google.android.gms.cast.** { *; }
-dontwarn com.google.android.gms.**
