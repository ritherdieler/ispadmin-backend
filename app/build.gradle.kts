plugins {
    id("gigafiber.spring-module")
    alias(libs.plugins.spring.boot)
    war
}

group = "com.dscorp.wispadmin"
version = "0.0.1-SNAPSHOT"

evaluationDependsOn(":core")

extra["gigafiberLayer"] = 5

springBoot {
    mainClass.set("com.dscorp.wispadmin.wispadmin.WispAdminApplicationKt")
}

dependencies {
    implementation(project(":acs"))
    implementation(project(":oltgateway"))
    implementation(project(":traffic"))
    implementation(project(":core"))
    implementation(project(":netdiag"))
    implementation(project(":observability"))
    developmentOnly(libs.spring.boot.devtools)
    providedRuntime(libs.spring.boot.starter.tomcat)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootWar>("bootWar") {
    enabled = false
}

fun isExcludedWarJar(name: String): Boolean {
    val djl = libs.versions.djl.get()
    val jni = libs.versions.pytorch.jni.get()
    return name == "api-$djl.jar" ||
        name == "pytorch-engine-$djl.jar" ||
        name == "pytorch-jni-$jni.jar" ||
        name.startsWith("pytorch-native-cpu-") ||
        name.startsWith("onnxruntime-")
}

fun isTomcatLibJar(name: String): Boolean {
    val djl = libs.versions.djl.get()
    val jni = libs.versions.pytorch.jni.get()
    return name == "api-$djl.jar" ||
        name == "pytorch-engine-$djl.jar" ||
        name == "pytorch-jni-$jni.jar" ||
        name.startsWith("pytorch-native-cpu-") ||
        name.startsWith("onnxruntime-") ||
        name.startsWith("slf4j-api-") ||
        (name.startsWith("gson-") && name.endsWith(".jar") && !name.contains("gson-extras")) ||
        (name.startsWith("jna-") && !name.contains("platform")) ||
        name.startsWith("commons-compress-")
}

tasks.named<War>("war") {
    enabled = true
    archiveFileName.set("ispadmin.war")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    rootSpec.exclude("WEB-INF/classes/application-local.properties")
    rootSpec.exclude("WEB-INF/classes/models/**")
    rootSpec.exclude("WEB-INF/classes/com/dscorp/wispadmin/wispadmin/util/PytorchNativeHelper.class")
    rootSpec.exclude("WEB-INF/classes/com/dscorp/wispadmin/wispadmin/util/PytorchNativeHelper\$*.class")
    rootSpec.exclude { details -> isExcludedWarJar(details.file.name) }
    finalizedBy("tomcatLibs")
}

val coreMainOutput = project(":core").extensions.getByType<SourceSetContainer>().named("main").map { it.output }

tasks.register<Jar>("djlNativeHelperJar") {
    dependsOn(project(":core").tasks.named("classes"))
    archiveBaseName.set("ispadmin")
    archiveVersion.set("")
    archiveClassifier.set("djl-native-helper")
    destinationDirectory.set(layout.buildDirectory.dir("tomcat-lib"))
    from(coreMainOutput) {
        include("com/dscorp/wispadmin/wispadmin/util/PytorchNativeHelper*.class")
    }
}

tasks.register<Copy>("tomcatLibs") {
    dependsOn(tasks.named("djlNativeHelperJar"))
    from(configurations.named("runtimeClasspath")) {
        include { element -> isTomcatLibJar(element.file.name) }
    }
    into(layout.buildDirectory.dir("tomcat-lib"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
