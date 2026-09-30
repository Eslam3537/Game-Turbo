import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

// 1. Single source of truth: read APP_VERSION from version.properties
val versionPropsFile = rootProject.file("version.properties")
if (!versionPropsFile.exists()) {
  throw GradleException("Missing 'version.properties' in root project directory. Create it with APP_VERSION=<number>.")
}

val versionProps = Properties().apply {
  versionPropsFile.inputStream().use { load(it) }
}

val appVersionRaw = versionProps.getProperty("APP_VERSION")?.trim()
  ?: throw GradleException("Missing 'APP_VERSION' property in version.properties.")

val appVersion = appVersionRaw.toIntOrNull()
if (appVersion == null || appVersion <= 0) {
  throw GradleException("Invalid APP_VERSION in version.properties: '$appVersionRaw'. Must be a positive integer (e.g. 1, 2, 3...).")
}

base {
  archivesName.set("Game-Turbo-v$appVersion")
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.gameturbo.kxmpzq"
    minSdk = 29
    targetSdk = 36
    versionCode = appVersion
    versionName = appVersion.toString()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  val keystorePath = System.getenv("KEYSTORE_PATH")
  val storePassword = System.getenv("STORE_PASSWORD")
  val keyPassword = System.getenv("KEY_PASSWORD")
  val keyAliasEnv = System.getenv("KEY_ALIAS") ?: "upload"

  val hasReleaseSigning = !keystorePath.isNullOrBlank() &&
      !storePassword.isNullOrBlank() &&
      !keyPassword.isNullOrBlank()

  signingConfigs {
    create("release") {
      if (hasReleaseSigning) {
        val ksFile = file(keystorePath!!)
        if (!ksFile.exists()) {
          throw GradleException("Specified KEYSTORE_PATH does not exist: ${ksFile.absolutePath}")
        }
        storeFile = ksFile
        this.storePassword = storePassword
        this.keyAlias = keyAliasEnv
        this.keyPassword = keyPassword
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      } else {
        // Enforce failure if release assembly is requested without signing credentials
        gradle.taskGraph.whenReady {
          val hasReleaseTask = allTasks.any { 
            it.name.contains("Release", ignoreCase = true) && !it.name.contains("UnitTest", ignoreCase = true)
          }
          if (hasReleaseTask) {
            throw GradleException(
              "Release signing credentials missing! KEYSTORE_PATH, STORE_PASSWORD, and KEY_PASSWORD " +
              "environment variables must all be set for release builds."
            )
          }
        }
      }
    }
    debug {
      // Default Android debug signing is used automatically
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  // Google Play In-App Update
  implementation("com.google.android.play:app-update:2.1.0")
  implementation("com.google.android.play:app-update-ktx:2.1.0")
  implementation(libs.okhttp)

  // Shizuku Privileged API
  implementation("dev.rikka.shizuku:api:13.1.5")
  implementation("dev.rikka.shizuku:provider:13.1.5")

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}
