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
        maven {
            name = "templateVendoredSdk"
            url = uri("vendor/maven")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "sts2-template-textension"
