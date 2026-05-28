import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.vanniktech.publish)
}

kotlin {
    jvmToolchain(11)
}

// ── Maven Central 배포 설정 ────────────────────────────────────────────────────
mavenPublishing {
    // groupId는 gradle.properties의 GROUP, version은 VERSION_NAME 에서 자동 읽음
    coordinates(
        artifactId = "kcandlekit-core",
    )

    pom {
        name = "KCandleKit Core"
        description = "Pure-Kotlin candle data model, pattern detection engine, and moving average indicators."
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

    // Sonatype 신규 Central Portal 사용 (2024년 이후 권장)
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    // 배포 시 GPG 서명 필수 (로컬 테스트는 publishToMavenLocal 사용)
    signAllPublications()
}

dependencies {
    testImplementation(libs.junit)
}
