plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace = "id.plegogel3.ruangkendali.parent"
 compileSdk = 35
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 } kotlinOptions { jvmTarget = "17" }
 defaultConfig { buildConfigField("String", "WORKER_BASE_URL", "\"https://YOUR-WORKER.workers.dev\""); buildConfigField("String", "FIREBASE_API_KEY", "\"CONFIGURE_FIREBASE_API_KEY\""); buildConfigField("String", "FIREBASE_APP_ID", "\"CONFIGURE_FIREBASE_APP_ID\""); buildConfigField("String", "FIREBASE_PROJECT_ID", "\"CONFIGURE_FIREBASE_PROJECT_ID\""); applicationId = "id.plegogel3.ruangkendali.parent"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
 buildFeatures { compose = true; buildConfig = true }
}
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("com.google.firebase:firebase-auth:23.1.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.compose.material3:material3")
}
