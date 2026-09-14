plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

group = "com.dscorp.wispadmin"
version = "0.0.1-SNAPSHOT"

tasks.named<Wrapper>("wrapper") {
    gradleVersion = "8.13"
    distributionType = Wrapper.DistributionType.BIN
}
