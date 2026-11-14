import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { id("com.android.application") }
android {
    namespace = "kr.nostalife.vehicleprobe"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    useLibrary("android.car", false)
    defaultConfig {
        applicationId = "kr.nostalife.vehicleprobe"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-probe"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
