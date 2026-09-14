plugins {
    id("gigafiber.spring-module")
}

extra["gigafiberLayer"] = 3

tasks.named<Jar>("jar") {
    exclude("**/PytorchNativeHelper.class")
    exclude("**/PytorchNativeHelper\$*.class")
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":events"))
    implementation(project(":transport"))
    implementation(project(":routeros"))
    implementation(project(":servicehealth"))
}
