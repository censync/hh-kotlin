import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
    signing
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

kotlin {
    explicitApi()
    compilerOptions {
        moduleName.set("io.github.censync.hh")
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjdk-release=1.8")
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // The golden vectors, and javax.imageio as an independent decoder without a display.
    systemProperty("hh.testdata", rootProject.file("testdata").absolutePath)
    systemProperty("java.awt.headless", "true")
    inputs.dir(rootProject.file("testdata"))
}

// Jars carry the licence and a module name, and are reproducible byte for byte.
tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(rootProject.file("LICENSE")) { into("META-INF") }
}
tasks.jar {
    manifest { attributes("Automatic-Module-Name" to "io.github.censync.hh") }
}

// Maven Central wants a javadoc jar. The API documentation is the KDoc of the sources jar; this jar says so.
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(rootProject.file("docs/javadoc-jar.md")) { rename { "README.md" } }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "hh"
            from(components["java"])
            artifact(javadocJar)
            pom {
                name.set("hh")
                description.set(
                    "Humanized Hash: a blockchain address or any hash as a deterministic picture a person can " +
                        "compare at a glance. Byte-identical with the C++ reference implementation.",
                )
                url.set("https://github.com/censync/hh-kotlin")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        name.set("Dmitry Mandrika")
                        email.set("dm@censync.com")
                        organization.set("CenSync")
                        organizationUrl.set("https://censync.com")
                    }
                }
                organization {
                    name.set("CenSync")
                    url.set("https://censync.com")
                }
                scm {
                    url.set("https://github.com/censync/hh-kotlin")
                    connection.set("scm:git:https://github.com/censync/hh-kotlin.git")
                    developerConnection.set("scm:git:ssh://git@github.com/censync/hh-kotlin.git")
                }
            }
        }
    }
    repositories {
        // A local repository that `centralBundle` zips for the Central Portal.
        maven {
            name = "staging"
            url = uri(layout.buildDirectory.dir("staging-repo"))
        }
    }
}

// Artefacts are signed when a key is given. An empty variable, as an absent CI secret gives, counts as absent.
//
//   HH_SIGNING_KEY       the secret key, ASCII-armoured, with its real line breaks (not "\n" in one line):
//                        gpg --armor --export-secret-keys <key id>
//   HH_SIGNING_PASSWORD  its passphrase, for a protected key
//   HH_SIGNING_KEY_ID    which key of the exported block signs: the last 8 hexadecimal digits of its id.
//                        Without it the primary key signs, which is right for the usual [SC] primary key. When
//                        the primary key can only certify and a subkey signs, name the subkey
//                        (gpg --list-secret-keys --keyid-format short); a signature of a certify-only key is
//                        one that no verifier accepts.
fun environment(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
val signingKey: String? = environment("HH_SIGNING_KEY")
signing {
    if (signingKey != null) {
        val password = providers.environmentVariable("HH_SIGNING_PASSWORD").getOrElse("")
        val keyId = environment("HH_SIGNING_KEY_ID")
        if (keyId != null) {
            useInMemoryPgpKeys(keyId.trim(), signingKey, password)
        } else {
            useInMemoryPgpKeys(signingKey, password)
        }
        sign(publishing.publications["maven"])
    }
}

// The staging repository is rebuilt from nothing, so that a bundle never carries files of an earlier
// version or signatures of an earlier build.
val cleanStaging by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.dir("staging-repo"))
}
tasks.matching { it.name == "publishMavenPublicationToStagingRepository" }.configureEach {
    dependsOn(cleanStaging)
}

// The upload bundle of the Central Portal (https://central.sonatype.com): the staging repository as a zip.
// Central accepts signed bundles only; an unsigned one is good for checking that the publication assembles.
tasks.register<Zip>("centralBundle") {
    dependsOn("publishMavenPublicationToStagingRepository")
    doLast {
        if (signingKey == null) {
            logger.warn("centralBundle: HH_SIGNING_KEY is not set, the bundle is unsigned and cannot be uploaded")
        }
    }
    from(layout.buildDirectory.dir("staging-repo")) {
        exclude("**/maven-metadata.xml*")
    }
    archiveFileName.set("hh-${project.version}-central-bundle.zip")
    destinationDirectory.set(layout.buildDirectory.dir("central"))
}

// The published artefact depends on kotlin-stdlib and on nothing else.
val verifyPom by tasks.registering {
    val pom = layout.buildDirectory.file("publications/maven/pom-default.xml")
    dependsOn("generatePomFileForMavenPublication")
    inputs.file(pom)
    doLast {
        val artifacts = Regex("<dependency>.*?<artifactId>([^<]+)</artifactId>", RegexOption.DOT_MATCHES_ALL)
            .findAll(pom.get().asFile.readText()).map { it.groupValues[1] }.toList()
        check(artifacts == listOf("kotlin-stdlib")) { "unexpected dependencies in the POM: $artifacts" }
    }
}
// The library is Kotlin standard library only: no JDK or Android imports and no floating point.
val verifySources by tasks.registering {
    val sources = fileTree("src/main/kotlin")
    inputs.files(sources)
    doLast {
        val forbidden = Regex("^import (java|javax|android)\\.|\\b(Float|Double)\\b|kotlin\\.math")
        val hits = sources.files.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                if (forbidden.containsMatchIn(line)) "${file.name}:${i + 1}: ${line.trim()}" else null
            }
        }
        check(hits.isEmpty()) { "forbidden in the library sources:\n" + hits.joinToString("\n") }
    }
}
tasks.check { dependsOn(verifyPom, verifySources) }
