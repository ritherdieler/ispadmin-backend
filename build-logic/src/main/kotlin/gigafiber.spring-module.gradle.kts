plugins {
    id("gigafiber.kotlin-library")
    id("io.spring.dependency-management")
    `java-library`
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val springBootVersion = catalog.findVersion("spring-boot").get().requiredVersion
val kotlinVersion = catalog.findVersion("kotlin").get().requiredVersion
val djlNativeClassifier = extra["djlNativeClassifier"] as String

extra["kotlin.version"] = kotlinVersion

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
    }
}

dependencies {
    api(catalog.findLibrary("poi").get())
    api(catalog.findLibrary("poi-ooxml").get())
    api(catalog.findLibrary("spring-boot-starter-data-jpa").get())
    api(catalog.findLibrary("datasource-proxy").get())
    api(catalog.findLibrary("spring-boot-starter-validation").get())
    api(catalog.findLibrary("spring-boot-starter-websocket").get())
    api(catalog.findLibrary("spring-boot-starter-actuator").get())
    api(catalog.findLibrary("firebase-admin").get())
    api(catalog.findLibrary("spring-boot-starter-security").get())
    api(catalog.findLibrary("lyra-server-rest-sdk").get())
    api(catalog.findLibrary("kotlin-reflect").get())
    api(catalog.findLibrary("kotlin-stdlib-jdk8").get())
    api(catalog.findLibrary("flyway-core").get())
    api(catalog.findLibrary("flyway-mysql").get())
    api(catalog.findLibrary("meilisearch-java").get())
    api(catalog.findLibrary("okhttp").get())
    api(catalog.findLibrary("spring-boot-starter-webflux").get())
    api(catalog.findLibrary("kotlinx-coroutines-core").get())
    api(catalog.findLibrary("kotlinx-coroutines-reactor").get())
    api(catalog.findLibrary("hibernate-spatial").get())
    api(catalog.findLibrary("jts-core").get())
    api(catalog.findLibrary("logback-classic").get())
    api(catalog.findLibrary("slf4j-api").get())
    api(catalog.findLibrary("janino").get())
    api(catalog.findLibrary("spring-boot-starter-aop").get())
    api(catalog.findLibrary("sshd-core").get())
    api(catalog.findLibrary("snmp4j").get())
    api(catalog.findLibrary("springdoc-openapi-ui").get())
    api(catalog.findLibrary("spring-boot-starter-cache").get())
    api(catalog.findLibrary("caffeine").get())
    api(catalog.findLibrary("spring-boot-starter-data-redis").get())
    api(catalog.findLibrary("jackson-module-kotlin").get())
    api(catalog.findLibrary("djl-api").get())
    api(catalog.findLibrary("djl-pytorch-engine").get())
    api(catalog.findLibrary("djl-onnxruntime-engine").get())
    runtimeOnly(catalog.findLibrary("mysql-connector-j").get())
    runtimeOnly(catalog.findLibrary("djl-pytorch-jni").get())
    runtimeOnly(variantOf(catalog.findLibrary("djl-pytorch-native-cpu").get()) { classifier(djlNativeClassifier) })
    testImplementation(catalog.findLibrary("spring-boot-starter-test").get())
    testImplementation(catalog.findLibrary("h2").get())
    testImplementation(catalog.findLibrary("mockk-jvm").get())
    testImplementation(catalog.findLibrary("okhttp-mockwebserver").get())
}

tasks.withType<Test>().configureEach {
    workingDir = rootProject.projectDir
}
