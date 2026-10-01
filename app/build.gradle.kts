import java.io.File
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

// 1. Single source of truth: read APP_VERSION from version.properties
val versionPropsFile = rootProject.file("version.properties")
if (!versionPropsFile.exists()) {
  throw GradleException("Missing 'version.properties' in root project directory. Create it with APP_VERSION=<positive_integer>.")
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

// 2. Load permanent release signing configuration from environment or keystore.properties fallback
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
  if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { load(it) }
  }
}

val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
  ?: keystoreProperties.getProperty("KEYSTORE_PATH")
  ?: keystoreProperties.getProperty("storeFile")

val releaseStorePassword = System.getenv("STORE_PASSWORD")
  ?: keystoreProperties.getProperty("STORE_PASSWORD")
  ?: keystoreProperties.getProperty("storePassword")

val releaseKeyAlias = System.getenv("KEY_ALIAS")
  ?: keystoreProperties.getProperty("KEY_ALIAS")
  ?: keystoreProperties.getProperty("keyAlias")
  ?: "upload"

val releaseKeyPassword = System.getenv("KEY_PASSWORD")
  ?: keystoreProperties.getProperty("KEY_PASSWORD")
  ?: keystoreProperties.getProperty("keyPassword")

val hasCompleteReleaseSigning = !releaseKeystorePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

base {
  archivesName.set("Game-Turbo-v$appVersion")
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    // DO NOT CHANGE - changing it breaks in-place updates.
    applicationId = "com.aistudio.pubgbooster.remix"
    minSdk = 29
    targetSdk = 36
    versionCode = appVersion
    versionName = appVersion.toString()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      if (hasCompleteReleaseSigning) {
        val ksFile = file(releaseKeystorePath!!)
        if (!ksFile.exists()) {
          throw GradleException("Release keystore file does not exist at: ${ksFile.absolutePath}")
        }
        storeFile = ksFile
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
        enableV1Signing = true
        enableV2Signing = true
        enableV3Signing = true
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasCompleteReleaseSigning) {
        signingConfig = signingConfigs.getByName("release")
      } else {
        // Enforce failure if assembleRelease or bundleRelease is invoked without signing credentials
        gradle.taskGraph.whenReady {
          val hasReleaseTask = allTasks.any { 
            it.name.contains("Release", ignoreCase = true) && !it.name.contains("UnitTest", ignoreCase = true)
          }
          if (hasReleaseTask) {
            val missing = mutableListOf<String>()
            if (releaseKeystorePath.isNullOrBlank()) missing.add("KEYSTORE_PATH")
            if (releaseStorePassword.isNullOrBlank()) missing.add("STORE_PASSWORD")
            if (releaseKeyPassword.isNullOrBlank()) missing.add("KEY_PASSWORD")
            throw GradleException(
              "Release signing credentials missing! The following required properties were not found " +
              "in environment variables or keystore.properties: [${missing.joinToString()}]. " +
              "Release builds must be signed with the permanent release keystore so updates can install over old versions."
            )
          }
        }
      }
    }
    debug {
      // Default Android debug keystore is used automatically (~/.android/debug.keystore)
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

abstract class PrintSigningInfoTask : DefaultTask() {
  @get:Input
  abstract val rootDirectoryPath: Property<String>

  @TaskAction
  fun printInfo() {
    val ksPath = System.getenv("KEYSTORE_PATH")
    val storePass = System.getenv("STORE_PASSWORD")
    val alias = System.getenv("KEY_ALIAS") ?: "upload"

    val propsFile = File(rootDirectoryPath.get(), "keystore.properties")
    val props = Properties().apply {
      if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
      }
    }

    val finalKsPath = ksPath ?: props.getProperty("KEYSTORE_PATH") ?: props.getProperty("storeFile")
    val finalStorePass = storePass ?: props.getProperty("STORE_PASSWORD") ?: props.getProperty("storePassword")
    val finalAlias = alias ?: props.getProperty("KEY_ALIAS") ?: props.getProperty("keyAlias") ?: "upload"

    if (finalKsPath.isNullOrBlank() || finalStorePass.isNullOrBlank()) {
      println("ERROR: Release signing credentials are not configured. Provide KEYSTORE_PATH and STORE_PASSWORD via environment variables or keystore.properties.")
      return
    }

    val ksFile = File(finalKsPath)
    if (!ksFile.exists()) {
      println("ERROR: Keystore file does not exist at: ${ksFile.absolutePath}")
      return
    }

    val process = ProcessBuilder(
      "keytool",
      "-list",
      "-v",
      "-keystore", ksFile.absolutePath,
      "-alias", finalAlias,
      "-storepass", finalStorePass
    ).redirectErrorStream(true).start()

    val output = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor()

    println("\n=== Game Turbo Release Keystore Information ===")
    println("Keystore path : ${ksFile.absolutePath}")
    println("Key alias     : $finalAlias")
    output.lines().filter { 
      it.contains("Owner:", ignoreCase = true) ||
      it.contains("Issuer:", ignoreCase = true) ||
      it.contains("Serial number:", ignoreCase = true) ||
      it.contains("Valid from:", ignoreCase = true) ||
      it.contains("SHA256:", ignoreCase = true) ||
      it.contains("SHA-256:", ignoreCase = true) ||
      it.contains("MD5:", ignoreCase = true)
    }.forEach { println(it.trim()) }
    println("================================================\n")
  }
}

tasks.register<PrintSigningInfoTask>("printSigningInfo") {
  description = "Prints the SHA-256 certificate fingerprint of the release keystore without exposing passwords."
  group = "help"
  rootDirectoryPath.set(rootProject.layout.projectDirectory.asFile.absolutePath)
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
