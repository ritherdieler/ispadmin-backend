plugins {
    id("gigafiber.spring-module")
}

extra["gigafiberLayer"] = 2

dependencies {
    implementation(project(":shared"))
}

tasks.processResources {
    from("../scripts/genieacs/provisions") {
        include("gf-onboarding-v2-*.js")
        into("genieacs/provisions")
    }
}
