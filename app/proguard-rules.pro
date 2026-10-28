# Proguard rules for RazDrop
-keep class com.razdrop.app.network.protocol.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
