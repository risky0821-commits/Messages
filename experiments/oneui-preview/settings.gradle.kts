pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        listOf("oneui-design", "sesl-androidx", "sesl-material-components-android").forEach { repo ->
            maven {
                url = uri("https://maven.pkg.github.com/tribalfs/$repo")
                credentials {
                    username = providers.environmentVariable("GITHUB_ACTOR").orNull
                    password = providers.environmentVariable("ONEUI_PACKAGES_TOKEN").orNull
                }
            }
        }
    }
}
rootProject.name = "SamaOneUiPreview"
