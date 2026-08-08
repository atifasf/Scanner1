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

# Apache POI and dependencies
-dontwarn java.awt.**
-dontwarn javax.xml.stream.**
-dontwarn net.sf.saxon.**
-dontwarn org.osgi.framework.**
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**


-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-keep class com.fasterxml.aalto.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class javax.xml.stream.** { *; }
-keep class com.fasterxml.aalto.stax.** { *; }
-dontwarn org.openxmlformats.**
-dontwarn schemaorg_apache_xmlbeans.**
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
