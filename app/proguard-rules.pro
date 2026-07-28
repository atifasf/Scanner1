# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Room entities, DAOs, and databases
-keep class * extends androidx.room.RoomDatabase
-keep class com.example.data.** { *; }
-dontwarn androidx.room.paging.**

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep model & AI classes
-keep class com.example.ui.ai.** { *; }
-keep class com.example.BuildConfig { *; }

