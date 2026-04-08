# Add project specific ProGuard rules here.
# Room and Hilt rules are auto-included via their Gradle plugins.

# Hilt
-keep public class * extends android.app.Service
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends androidx.lifecycle.ViewModel

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Entity
-keep @androidx.room.Entity class * { *; }

# Keeping all classes that might be used by Hilt for reflection
-keep class com.babytracker.** { *; }
-keep interface com.babytracker.** { *; }
