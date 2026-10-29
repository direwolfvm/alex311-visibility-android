plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}
android {
    namespace = "com.herbertindustries.alex311"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.herbertindustries.alex311"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${providers.gradleProperty("googleWebClientId").orElse("650621702399-f23j3l0mv6uv63b19d1chst41pv4shem.apps.googleusercontent.com").get()}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

// Pin transitive dependencies so public CI can audit the exact resolved graph.
dependencyLocking { lockAllConfigurations() }

// AGP 8.13's device-test runner still requests vulnerable Guava, 4.1.x Netty,
// and 3.x Protobuf versions. Keep these compatible security patches scoped to
// UTP tooling; they do not add dependencies to the shipped Android app.
// Reassess when upgrading AGP; the lockfile audit also covers these configs.
configurations.configureEach {
    if (name.startsWith("_internal-unified-test-platform")) {
        resolutionStrategy.eachDependency {
            when {
                requested.group == "com.google.guava" && requested.name == "guava" -> {
                    useVersion("33.7.2-jre")
                    because("Fix CVE-2026-102554 in AGP device-test tooling")
                }
                requested.group == "io.netty" && requested.version?.startsWith("4.1.") == true -> {
                    useVersion("4.1.139.Final")
                    because("Fix known Netty vulnerabilities in AGP device-test tooling")
                }
                requested.group == "com.google.protobuf" && requested.version?.startsWith("3.") == true -> {
                    useVersion("3.25.9")
                    because("Fix Protobuf denial-of-service vulnerabilities in AGP device-test tooling")
                }
            }
        }
    }
}
