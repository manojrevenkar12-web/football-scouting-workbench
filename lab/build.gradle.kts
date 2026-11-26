plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":world"))
    implementation("tools.jackson.core:jackson-databind")
    implementation("org.postgresql:postgresql")
}

application {
    mainClass.set("scout.lab.Cli")
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
    maxHeapSize = "3g"
}
