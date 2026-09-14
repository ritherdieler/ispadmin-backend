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
    ":events",
    ":transport",
    ":routeros",
    ":servicehealth",
    ":acs",
    ":oltgateway",
    ":traffic",
    ":core",
    ":netdiag",
    ":observability",
    ":app",
)
