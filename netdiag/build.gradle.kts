plugins {
    id("gigafiber.spring-module")
}

extra["gigafiberLayer"] = 4

dependencies {
    implementation(project(":core"))
    implementation(project(":servicehealth"))
    implementation(project(":routeros"))
    implementation(project(":shared"))
    implementation(project(":transport"))
    testImplementation(project(":oltgateway"))
    testImplementation(testFixtures(project(":routeros")))
}
