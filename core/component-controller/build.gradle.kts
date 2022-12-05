plugins {
    id("blocker.android.library")
    id("blocker.android.library.jacoco")
    id("blocker.android.hilt")
    id("kotlinx-serialization")
}

android {
    namespace = "com.merxury.blocker.core.componentcontroller"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:ifw-api"))

    testImplementation(project(":core:testing"))

    implementation(libs.androidx.core.ktx)

    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.libsu.core)
    implementation(libs.libsu.io)
    implementation(libs.xlog)
}