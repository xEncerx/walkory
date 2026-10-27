plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

tasks.register("checkDebug") {
    group = "verification"
    description = "Builds the debug app and runs Android lint and local unit tests."
    dependsOn(":app:assembleDebug", ":app:lintDebug", ":app:testDebugUnitTest")
}
