rootProject.name = "radar-push-endpoint"

include(":deprecated-javax")

pluginManagement {
    val kotlinVersion: String by settings
    plugins {
        kotlin("jvm") version kotlinVersion
    }
}
