plugins {
    id("gigafiber.spring-module")
}

extra["gigafiberLayer"] = 2

dependencies {
    implementation(project(":shared"))
    implementation(project(":events"))
    implementation(project(":transport"))
}
