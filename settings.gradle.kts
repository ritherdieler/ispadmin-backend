pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven {
            name = "jitpack"
            url = uri("https://jitpack.io")
        }
    }
}

rootProject.name = "ispadmin"
include(
    ":shared",
    ":acs",
    ":oltgateway",
    ":traffic",
    ":core",
)
