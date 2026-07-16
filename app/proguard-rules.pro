# JavaCV / JavaCPP (FFmpeg native binary loader)
-keep class org.bytedeco.** { *; }
-dontwarn org.bytedeco.**

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
