@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.compose.hot-reload")
  id("org.jetbrains.compose")
  alias(libs.plugins.ksp)
  `maven-publish`
}

group = "com.google.fhir"

version = "1.0.0-alpha01"

kotlin {
  jvmToolchain(21)

  androidLibrary {
    namespace = "com.google.android.fhir.datacapture"
    compileSdk = Sdk.COMPILE_SDK
    minSdk = Sdk.MIN_SDK
    withJava()
    withHostTestBuilder {}
    withDeviceTestBuilder { sourceSetTreeName = "test" }
      .configure { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }

    experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true

    compilations.configureEach {
      compilerOptions.configure {
        jvmTarget.set(
          org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11,
        )
      }
    }

    packaging {
      resources.excludes.addAll(
        listOf("META-INF/ASL2.0", "META-INF/ASL-2.0.txt", "META-INF/LGPL-3.0.txt"),
      )
    }
  }

  val xcfName = "datacaptureKmp"

  iosX64 { binaries.framework { baseName = xcfName } }

  iosArm64 { binaries.framework { baseName = xcfName } }

  iosSimulatorArm64 { binaries.framework { baseName = xcfName } }

  wasmJs {
    browser()
    binaries.library()
  }

  jvm("desktop")

  js {
    browser()
    binaries.library()
  }

  sourceSets {
    all {
      languageSettings {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
      }
    }

    commonMain {
      dependencies {
        implementation(libs.material.icons.extended)
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.ui)
        implementation(compose.components.resources)
        implementation(compose.components.uiToolingPreview)
        implementation(libs.fhir.path)
        implementation(libs.navigation.compose)
        implementation(libs.androidx.lifecycle.viewmodel.compose)
        implementation(libs.androidx.lifecycle.runtime.compose)
        implementation(libs.filekit.dialogs.compose)
        implementation(libs.kermit)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlin.fhir)
        implementation(libs.kotlinx.io.core)
        implementation(libs.kotlinx.serialization.json)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.androidx.lifecycle.runtime.testing)
        implementation(libs.kotlin.test)
        implementation(libs.kotest.assertions.core)

        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.uiTest)
      }
    }

    androidMain { resources.srcDir("res") }

    getByName("androidDeviceTest") {
      dependencies {
        implementation(libs.androidx.compose.ui.test.junit4)
        implementation(libs.androidx.compose.ui.test.manifest)
        implementation(libs.androidx.test.core)
        implementation(libs.androidx.test.ext.junit)
        implementation(libs.androidx.test.ext.junit.ktx)
        implementation(libs.androidx.test.runner)
        implementation(libs.androidx.test.rules)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.truth)
      }
    }

    getByName("androidHostTest") {
      dependencies {
        implementation(libs.androidx.fragment.testing)
        implementation(libs.androidx.test.core)
        implementation(libs.junit)
        implementation(libs.kotlin.test.junit)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.truth)
      }
    }

    val desktopMain by getting {
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(libs.kotlinx.coroutines.swing)
      }
    }
  }
}

// publishing prep
val localRepo: Directory = project.layout.buildDirectory.get().dir("repo")

publishing {
  repositories { maven { url = localRepo.asFile.toURI() } }
  publications {
    withType<MavenPublication> {
      pom {
        licenses {
          license {
            name.set("The Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
          }
        }
      }
    }
  }
}

val deleteRepoTask =
  tasks.register<Delete>("deleteLocalRepo") {
    description =
      "Deletes the local repository to get rid of stale artifacts before local publishing"
    this.delete(localRepo)
  }

tasks.named("publishAllPublicationsToMavenRepository").configure { dependsOn(deleteRepoTask) }

tasks.register("zipRepo", Zip::class) {
  description = "Create a zip of the maven repository"
  this.destinationDirectory.set(project.layout.buildDirectory.dir("repoZip"))
  archiveBaseName.set("kotlin-data-capture")

  // Hint to gradle that the repo files are produced by the publish task. This establishes a
  // dependency from the zipRepo task to the publish task.
  this.from(
    tasks.named("publish").map { _ -> localRepo },
  )
}
