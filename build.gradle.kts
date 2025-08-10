plugins {
    kotlin("jvm") version "1.9.24"
    application
    id("org.openjfx.javafxplugin") version "0.0.14"
}
repositories { mavenCentral() }
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
}
application { mainClass.set("ChatKt") }
kotlin {
    jvmToolchain(21)
}
javafx {
    version = "21.0.3"
    modules = listOf("javafx.controls", "javafx.web")
}