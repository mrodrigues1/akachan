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

# Hilt generates a `BabyTrackerApp_HiltComponents` holder whose nested subcomponent builders
# (ActivityC$Builder, FragmentC$Builder, ViewModelC$Builder, …) and the `Hilt_BabyTrackerApp`
# base class are compile-time scaffolding. Under R8 full mode these are reported as "missing
# classes" because they are referenced but not retained as standalone types — they are not
# needed at runtime. Suppress per AGP's auto-generated missing_rules.txt so the minified release
# build (`minifyReleaseWithR8`) succeeds.
-dontwarn com.babytracker.BabyTrackerApp_HiltComponents$**
-dontwarn com.babytracker.Hilt_BabyTrackerApp
