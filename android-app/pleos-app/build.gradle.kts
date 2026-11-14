import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "kr.nostalife.companion"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    useLibrary("android.car", false)
    defaultConfig {
        applicationId = "kr.nostalife.pleos"
        minSdk = 29
        targetSdk = 34
        versionCode = 6
        versionName = "4.2.0"
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"http://10.0.2.2:4181\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes { debug { applicationIdSuffix = ".debug" }; release { isMinifyEnabled = false } }
    sourceSets {
        getByName("main") {
            kotlin.srcDir("../app/src/main/java")
            kotlin.srcDir("../vehicle-probe/src/main/java")
            res.srcDirs("../app/src/main/res")
        }
        getByName("debug") { res.srcDirs("../app/src/debug/res") }
        getByName("test") { resources.srcDir("../app/src/test/resources") }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isReturnDefaultValues = true }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
