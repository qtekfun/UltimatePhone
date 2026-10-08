pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "UltimatePhone"
include(":app")
include(":core:designsystem")
include(":core:phonenumber")
include(":core:telecom")
include(":core:contacts")
include(":core:calllog")
