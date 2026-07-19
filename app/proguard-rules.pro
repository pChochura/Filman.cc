# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# -------------------------------------------------------------------------
# kotlinx.serialization
# -------------------------------------------------------------------------
# Keep the names and fields of all @Serializable classes to prevent R8 from
# renaming data models and navigation routes, which breaks JSON parsing and Navigation Compose.
-keepattributes *Annotation*, InnerClasses

-keepnames @kotlinx.serialization.Serializable class *

-keepclassmembers @kotlinx.serialization.Serializable class * {
    <fields>;
}

# Keep the generated serializers
-keepclassmembers class *$$serializer {
    public static final *** INSTANCE;
}

# -------------------------------------------------------------------------
# Jsoup
# -------------------------------------------------------------------------
# JSoup heavily relies on reflection for DOM manipulation
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
