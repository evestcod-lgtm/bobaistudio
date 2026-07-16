# FFmpegKit
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**

# Retrofit / OkHttp / Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.bobai.studio.ai.** { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
