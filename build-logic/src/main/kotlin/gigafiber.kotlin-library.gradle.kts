import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

sourceSets.named("main") {
    java.srcDir("src/main/kotlin")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        excludeTags("live-mk1", "local-db")
    }
}

if (!extra.has("gigafiberLayer")) {
    extra["gigafiberLayer"] = 0
}

extra["djlNativeClassifier"] = djlNativeClassifier()

fun djlNativeClassifier(): String {
    if (hasProperty("djl.linux.aarch64")) {
        return "linux-aarch64"
    }
    if (hasProperty("djl.linux")) {
        return "linux-x86_64"
    }
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val isMac = os.contains("mac")
    val isLinux = os.contains("nux")
    val isArm = arch == "aarch64" || arch == "arm64"
    return when {
        isMac && isArm -> "osx-aarch64"
        isMac -> "osx-x86_64"
        isLinux && isArm -> "linux-aarch64"
        else -> "linux-x86_64"
    }
}
