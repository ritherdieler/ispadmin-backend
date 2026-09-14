plugins {
    id("gigafiber.spring-module")
}

extra["gigafiberLayer"] = 3

dependencies {
    implementation(project(":events"))
    implementation(project(":routeros"))
    implementation(project(":shared"))
}
