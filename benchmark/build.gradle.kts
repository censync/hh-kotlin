// Stretching benchmark on the JVM and, through d8 and app_process, on an Android device.
// Never published.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

// The benchmark measures the library's internal PBKDF2 at several iteration counts, which the
// public API does not expose; the hh module is therefore a friend module of this one.
tasks.named<KotlinCompile>("compileKotlin") {
    friendPaths.from(project(":hh").layout.buildDirectory.dir("classes/kotlin/main"))
    friendPaths.from(project(":hh").layout.buildDirectory.dir("libs"))
}

application {
    mainClass.set("io.github.censync.hh.benchmark.StretchBenchmarkKt")
}

// The jars an Android device needs, for benchmark/run-on-device.sh.
tasks.register<Sync>("deviceJars") {
    from(tasks.named("jar"))
    from(configurations.named("runtimeClasspath"))
    into(layout.buildDirectory.dir("device"))
}
