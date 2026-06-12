import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.vanniktech.publish)
}

android {
    namespace = "com.hanto.kcandlekit.compose"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

// ── Maven Central 배포 설정 ────────────────────────────────────────────────────
mavenPublishing {
    coordinates(
        artifactId = "kcandlekit-compose",
    )

    pom {
        name = "KCandleKit Compose"
        description = "Jetpack Compose candlestick chart with MA lines, pattern markers, crosshair, and time axis."
        inceptionYear = "2025"
        url = providers.gradleProperty("POM_URL")

        licenses {
            license {
                name = providers.gradleProperty("POM_LICENCE_NAME")
                url  = providers.gradleProperty("POM_LICENCE_URL")
                distribution = providers.gradleProperty("POM_LICENCE_URL")
            }
        }
        developers {
            developer {
                id   = providers.gradleProperty("POM_DEVELOPER_ID")
                name = providers.gradleProperty("POM_DEVELOPER_NAME")
                url  = providers.gradleProperty("POM_URL")
            }
        }
        scm {
            url                 = providers.gradleProperty("POM_SCM_URL")
            connection          = providers.gradleProperty("POM_SCM_CONNECTION")
            developerConnection = providers.gradleProperty("POM_SCM_DEV_CONNECTION")
        }
    }

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    if (project.hasProperty("signing.keyId")) signAllPublications()
}

dependencies {
    // 배포된 POM에서 project(":core") → io.github.YOUR_GITHUB_ID:kcandlekit-core:VERSION 으로 자동 변환됨
    api(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
