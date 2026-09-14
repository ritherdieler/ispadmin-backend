plugins {
    id("gigafiber.spring-module")
}

extra["gigafiberLayer"] = 2

dependencies {
    implementation(project(":shared"))
}
