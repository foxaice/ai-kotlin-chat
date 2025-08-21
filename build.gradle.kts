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

    // Зависимости для тестирования
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.20")
}
application { mainClass.set("ChatKt") }
kotlin {
    jvmToolchain(21)
}

// ---- Day 8: run the Telegram job scheduler ----
tasks.register<JavaExec>("runJob") {
    group = "application"
    description = "Run Day8 job: every 20s run ChatKt main with '/todoist todayTasks' and send result to Telegram"
    classpath = sourceSets["main"].runtimeClasspath
    // job.JobMainKt is the top-level main in src/main/kotlin/job/JobMain.kt
    mainClass.set("job.JobMainKt")
    // You can override interval, input, etc. via --args (see README)
}

// ---- Новая задача: запуск агента ----
tasks.register<JavaExec>("runAgent") {
    group = "application"
    description = "Run system agent with Gemini integration"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ChatKt")
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
        showStandardStreams = true
    }
}