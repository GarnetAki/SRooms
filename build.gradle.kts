import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("org.jetbrains.compose") version "1.8.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
}

group = "srooms"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

val ktorVersion = "3.1.3"
val skikoVersion = "0.9.4.2" // должна совпадать с версией, которую тянет Compose (см. dependencies)

val extraNatives = configurations.create("extraNatives")

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets-jvm:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    implementation("org.slf4j:slf4j-simple:2.0.17")

    // нативные рантаймы Skiko для всеплатформенного uber-jar (см. packageUberJarAllOs)
    extraNatives("org.jetbrains.skiko:skiko-awt-runtime-macos-x64:$skikoVersion")
    extraNatives("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:$skikoVersion")
    extraNatives("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:$skikoVersion")
    extraNatives("org.jetbrains.skiko:skiko-awt-runtime-linux-arm64:$skikoVersion")
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "srooms.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "SRooms"
            packageVersion = "1.0.0"
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

tasks.register<JavaExec>("runSmokeTest") {
    group = "verification"
    mainClass.set("srooms.tools.SmokeTestKt")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
}

/** Всеплатформенный uber-jar: windows-билд + нативки Skiko для macOS/Linux.
 *  Запускается везде, где есть Java 17+: `java -jar SRooms-all-os-1.0.0.jar`. */
tasks.register<Jar>("packageUberJarAllOs") {
    group = "distribution"
    description = "Uber-jar с нативными библиотеками Skiko для Windows, macOS и Linux"
    dependsOn("packageUberJarForCurrentOS")
    from(zipTree(tasks.named("packageUberJarForCurrentOS").get().outputs.files.singleFile))
    extraNatives.files.forEach { from(zipTree(it)) }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("SRooms-all-os-${project.version}.jar")
    manifest { attributes("Main-Class" to "srooms.MainKt") }
    destinationDirectory.set(layout.buildDirectory.dir("compose/jars"))
}
