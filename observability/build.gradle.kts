plugins {
    id("gigafiber.spring-module")
}

extra["gigafiberLayer"] = 4

dependencies {
    implementation(project(":core"))
    implementation(project(":shared"))
}
