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

# First-party reflection/serialization surfaces.
#
# The previous blanket `-keep class com.babytracker.** { *; }` (+ interface) froze ALL app code:
# no renaming, inlining, or dead-code removal, so R8 could never strip unused screens/use
# cases/receivers. Hilt, Room, Glance, WorkManager and Firebase ship their own consumer rules, so
# the blanket keep was largely redundant. These targeted keeps preserve only what is actually
# reached reflectively at runtime, letting R8 shrink and rename everything else.

# Enums are persisted by constant name (DataStore, Room TypeConverters, Firestore maps,
# kotlinx.serialization backups). Keep their members — chiefly the constant fields and the
# values()/valueOf() pair — so name()/valueOf() round-trips never break. The enum class itself may
# still be renamed; only the persisted constant names must survive.
-keepclassmembers enum com.babytracker.** {
    *;
}

# kotlinx.serialization @Serializable models (backup export/import). The plugin emits compile-time
# serializers (the JSON keys are baked into the serializer as string constants, so renaming model
# fields is harmless) and kotlinx-serialization ships consumer rules that keep those serializers +
# Companion accessors. Keeping the annotated classes whole here is belt-and-suspenders: it guarantees
# the model types and their members are never stripped, independent of the bundled rules.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses
-keep @kotlinx.serialization.Serializable class com.babytracker.** { *; }

# WorkManager persists a worker's class name and resolves it (via the Hilt WorkerFactory) at runtime,
# so workers must not be renamed or stripped.
-keep class * extends androidx.work.ListenableWorker { *; }

# Hilt generates a `BabyTrackerApp_HiltComponents` holder whose nested subcomponent builders
# (ActivityC$Builder, FragmentC$Builder, ViewModelC$Builder, …) and the `Hilt_BabyTrackerApp`
# base class are compile-time scaffolding. Under R8 full mode these are reported as "missing
# classes" because they are referenced but not retained as standalone types — they are not
# needed at runtime. Suppress per AGP's auto-generated missing_rules.txt so the minified release
# build (`minifyReleaseWithR8`) succeeds.
-dontwarn com.babytracker.BabyTrackerApp_HiltComponents$**
-dontwarn com.babytracker.Hilt_BabyTrackerApp
