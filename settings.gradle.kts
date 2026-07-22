pluginManagement {
    val useKronosMavenLocal = gradle.startParameter.projectProperties["kronosUseMavenLocal"] == "true"
    repositories {
        if (useKronosMavenLocal) {
            mavenLocal()
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val useKronosMavenLocal = gradle.startParameter.projectProperties["kronosUseMavenLocal"] == "true"
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useKronosMavenLocal) {
            mavenLocal()
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "kronos-example-android"
include(":app")
