plugins {
    kotlin("jvm") version "2.2.0"
    application
}
repositories { mavenCentral() }
dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:2.2.0"))
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")
    implementation("com.vladsch.flexmark:flexmark-all:0.64.8")
    implementation("io.modelcontextprotocol:kotlin-sdk:0.6.0")
    implementation("io.modelcontextprotocol:kotlin-sdk-jvm:0.6.0")
}
application { mainClass.set("ChatKt") }
kotlin {
    jvmToolchain(21)
}