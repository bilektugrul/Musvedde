plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.baselineprofile)
}

val devtoolsBuildTypes = setOf("debug", "benchmarkRelease", "nonMinifiedRelease")

android {
    namespace = "dev.aliarslan.musvedde"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.aliarslan.musvedde"
        minSdk = 29
        targetSdk = 36
        versionCode = 5
        versionName = "0.3.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf("en", "tr")
    }

    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "768m"
            it.jvmArgs("-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1")
            providers.gradleProperty("robolectricDependencyDir").orNull?.let { dir ->
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty("robolectric.dependency.dir", dir)
            }
        }
    }
}

androidComponents {
    onVariants { variant ->
        if (variant.buildType in devtoolsBuildTypes) {
            variant.sources.kotlin?.addStaticSourceDirectory("src/devtools/java")
            variant.sources.manifests.addStaticManifestFile("src/devtools/AndroidManifest.xml")
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("app/compose-stability.conf"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.reorderable)
    implementation(libs.androidx.hilt.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.documentfile)
    implementation(libs.okhttp)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
