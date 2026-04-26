plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

val ktlintVersion = libs.versions.ktlint.get()

subprojects {
    plugins.withId("com.android.application") {
        apply(plugin = "org.jlleitschuh.gradle.ktlint")
        apply(plugin = "io.gitlab.arturbosch.detekt")

        configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set(ktlintVersion)
            android.set(true)
            outputToConsole.set(true)
        }

        configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
            buildUponDefaultConfig = true
            config.setFrom("$rootDir/config/detekt.yml")
        }
    }
}
