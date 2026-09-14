plugins {
    id("gigafiber.spring-module")
    alias(libs.plugins.spring.boot)
    war
}

group = "com.dscorp.wispadmin"
version = "0.0.1-SNAPSHOT"

extra["gigafiberLayer"] = 3

springBoot {
    mainClass.set("com.dscorp.wispadmin.wispadmin.WispAdminApplicationKt")
}

tasks.named<Jar>("jar") {
    enabled = false
    exclude("**/PytorchNativeHelper.class")
    exclude("**/PytorchNativeHelper\$*.class")
}

dependencies {
    implementation(project(":shared"))
    runtimeOnly(project(":acs"))
    runtimeOnly(project(":oltgateway"))
    runtimeOnly(project(":traffic"))
    testImplementation(project(":oltgateway"))
    testImplementation(testFixtures(project(":shared")))
    developmentOnly(libs.spring.boot.devtools)
    providedRuntime(libs.spring.boot.starter.tomcat)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootWar>("bootWar") {
    enabled = false
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
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

val warClasses = tasks.register<Sync>("warClasses") {
    dependsOn(tasks.named("classes"))
    from(sourceSets.named("main").map { it.output })
    exclude("application-local.properties")
    exclude("application-local-prestaging.properties")
    exclude("application-local-prestaging.secrets.properties")
    exclude("application-local-prestaging.secrets.properties.example")
    exclude("models/**")
    exclude("**/PytorchNativeHelper.class")
    exclude("**/PytorchNativeHelper\$*.class")
    into(layout.buildDirectory.dir("war-classes"))
}

tasks.named<War>("war") {
    enabled = true
    archiveFileName.set("ispadmin.war")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(warClasses)
    classpath = files(layout.buildDirectory.dir("war-classes")) +
        (configurations.getByName("runtimeClasspath") - configurations.getByName("providedRuntime"))
            .filter { it.name.endsWith(".jar") && !isExcludedWarJar(it.name) }
    exclude("application-local.properties")
    exclude("application-local-prestaging.properties")
    exclude("application-local-prestaging.secrets.properties")
    exclude("application-local-prestaging.secrets.properties.example")
    exclude("models/**")
    exclude("com/dscorp/wispadmin/wispadmin/util/PytorchNativeHelper.class")
    exclude("com/dscorp/wispadmin/wispadmin/util/PytorchNativeHelper\$*.class")
    rootSpec.exclude("WEB-INF/classes/application-local.properties")
    rootSpec.exclude("WEB-INF/classes/application-local-prestaging.properties")
    rootSpec.exclude("WEB-INF/classes/application-local-prestaging.secrets.properties")
    rootSpec.exclude("WEB-INF/classes/application-local-prestaging.secrets.properties.example")
    rootSpec.exclude("WEB-INF/classes/models/**")
    rootSpec.exclude("WEB-INF/classes/com/dscorp/wispadmin/wispadmin/util/PytorchNativeHelper.class")
    rootSpec.exclude("WEB-INF/classes/com/dscorp/wispadmin/wispadmin/util/PytorchNativeHelper\$*.class")
    rootSpec.exclude { details -> isExcludedWarJar(details.file.name) }
    filesMatching("**/application-local.properties") { exclude() }
    filesMatching("**/application-local-prestaging*") { exclude() }
    filesMatching("**/models/**") { exclude() }
    filesMatching("**/PytorchNativeHelper*.class") { exclude() }
    finalizedBy("tomcatLibs")
}

tasks.register<Jar>("djlNativeHelperJar") {
    dependsOn(tasks.named("classes"))
    archiveBaseName.set("ispadmin")
    archiveVersion.set("")
    archiveClassifier.set("djl-native-helper")
    destinationDirectory.set(layout.buildDirectory.dir("tomcat-lib"))
    from(sourceSets.named("main").map { it.output }) {
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
