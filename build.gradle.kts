// Root build.gradle.kts
// Keine Plugins, keine Repositories

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}
