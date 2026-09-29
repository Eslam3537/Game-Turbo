# Shizuku keep rules
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class * implements rikka.shizuku.Shizuku$* { *; }
-dontwarn rikka.shizuku.**

# Room keep rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Data & Entity classes
-keepclassmembers class com.example.data.** { *; }
-keep class com.example.data.** { *; }
-keep class com.example.engine.** { *; }
