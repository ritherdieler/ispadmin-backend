plugins {
    id("gigafiber.spring-module")
    `java-test-fixtures`
}

extra["gigafiberLayer"] = 1

dependencies {
    testFixturesImplementation(libs.spring.boot.starter.test)
}

