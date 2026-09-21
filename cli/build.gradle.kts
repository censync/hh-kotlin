// Command line tool for the differential test against hh-cpp (tools/crosscheck.sh). It uses the public API
// only. Never published.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjdk-release=1.8")
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(project(":hh"))
}

application {
    mainClass.set("io.github.censync.hh.cli.MainKt")
}
