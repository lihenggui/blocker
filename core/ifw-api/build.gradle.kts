plugins {
    id("blocker.android.library")
    id("blocker.android.library.jacoco")
    id("blocker.android.hilt")
}

android {
    namespace = "com.merxury.blocker.core.ifw"
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    testImplementation(project(":core:testing"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.simplexml) {
        exclude("stax", "stax")
        exclude("stax-api", "stax-api")
        exclude("xpp3", "xpp3")
    }
    implementation(libs.libsu.core)
    implementation(libs.libsu.io)
    implementation(libs.xlog)
}