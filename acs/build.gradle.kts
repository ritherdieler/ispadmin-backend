plugins {
    id("gigafiber.spring-module")
}

extra["gigafiberLayer"] = 3

dependencies {
    implementation(project(":events"))
    implementation(project(":transport"))
    implementation(project(":shared"))
}
