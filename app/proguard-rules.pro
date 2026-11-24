# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Room database, entities, DAOs, and generated implementations
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class com.example.data.* { *; }
-dontwarn androidx.room.paging.**

# DataStore Preferences / Proto
-keep class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite { *; }

# Jetpack Glance AppWidget & Receivers
-keep class com.example.widget.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class * implements androidx.glance.appwidget.action.ActionCallback { public <init>(); }

# Manifest components, notification receivers, foreground guard service & WorkManager workers
-keep class com.example.ScheduleApp { *; }
-keep class com.example.MainActivity { *; }
-keep class com.example.notification.** { *; }
-keep class * extends androidx.work.CoroutineWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }

# Keep source file and line numbers for readable crash diagnostics
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod
