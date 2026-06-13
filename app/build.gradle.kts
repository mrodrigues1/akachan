import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kover)
    alias(libs.plugins.android.junit5)
}

abstract class NormalizeAndroidTestConfigPaths : DefaultTask() {
    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val configDirectory: DirectoryProperty

    @TaskAction
    fun normalize() {
        if (System.getProperty("os.name").startsWith("Windows")) return

        configDirectory.asFile
            .get()
            .walkTopDown()
            .filter { it.name == "test_config.properties" && it.path.endsWith("com/android/tools/test_config.properties") }
            .forEach { configFile ->
                configFile.writeText(configFile.readText().replace("\\\\", "/"))
            }
    }
}

android {
    namespace = "com.babytracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.babytracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 71
        versionName = "1.52.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["runnerBuilder"] =
            "de.mannodermaus.junit5.AndroidJUnit5Builder"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("SIGNING_KEYSTORE") ?: "release.keystore")
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: ""
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: ""
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val signingEnvPresent = System.getenv("SIGNING_KEY_ALIAS") != null
            if (signingEnvPresent) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    // Drag-and-drop reordering
    implementation(libs.reorderable)
    debugImplementation(libs.compose.ui.tooling)

    // Core
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)

    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.ext.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Glance (widgets)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Unit Testing
    testImplementation(libs.work.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.konsist)
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.glance.appwidget.testing)
    testRuntimeOnly(libs.junit.vintage.engine)
    detektPlugins(libs.detekt.compose.rules)

    // Android Testing
    androidTestImplementation(libs.runner)
    androidTestImplementation(libs.rules)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.test.rules)
    androidTestImplementation(libs.espresso.intents)
    androidTestImplementation(libs.room.testing)

    // Android Testing — JUnit 5 support
    androidTestImplementation(libs.junit5.api)
    androidTestRuntimeOnly(libs.junit5.engine)
    androidTestImplementation(libs.mannodermaus.core)
    androidTestRuntimeOnly(libs.mannodermaus.runner)
    androidTestImplementation(libs.coroutines.test)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.coroutines.play.services)
}

// Konsist scope creation costs ~14 minutes on WSL2 NTFS. Opt out of the
// architecture tests with `-PfastTests` (bare flag or `=true`/`=1`/`=yes`)
// for fast dev loops. `-PfastTests=false`, `=0`, `=no` keeps them. CI omits
// the flag and runs the full suite.
val skipArchitectureTests: Boolean =
    project
        .findProperty("fastTests")
        ?.toString()
        ?.lowercase()
        ?.let { it.isEmpty() || it in setOf("true", "1", "yes") } ?: false

val normalizeAndroidTestConfigPaths =
    tasks.register<NormalizeAndroidTestConfigPaths>("normalizeAndroidTestConfigPaths") {
        configDirectory.set(layout.buildDirectory.dir("intermediates/unit_test_config_directory"))
    }

tasks.withType<Test> {
    useJUnitPlatform {
        if (skipArchitectureTests) {
            excludeTags("architecture")
        }
    }
    if (!System.getProperty("os.name").startsWith("Windows")) {
        dependsOn(normalizeAndroidTestConfigPaths)
        normalizeAndroidTestConfigPaths.configure {
            mustRunAfter(tasks.matching { it.name == "generateDebugUnitTestConfig" })
        }
    }
    // Konsist scope parsing keeps every Kotlin AST in memory — default 512m
    // forces GC churn that visibly slows architecture tests, especially on WSL2.
    maxHeapSize = "2g"
    jvmArgs("-XX:+UseG1GC", "-XX:MaxMetaspaceSize=512m")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "*_HiltComponents*",
                    "*_MembersInjector*",
                    "*Hilt_*",
                    "*.di.*",
                    "*.BabyTrackerApp",
                    "*.MainActivity",
                    "*.BuildConfig",
                )
            }
        }
        verify {
            rule("Minimum line coverage") {
                bound {
                    minValue = 60
                }
            }
        }
    }
}
