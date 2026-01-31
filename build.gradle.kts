import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.blockbuster"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Dropwizard 4
    implementation(platform("io.dropwizard:dropwizard-bom:4.0.16"))
    implementation("io.dropwizard:dropwizard-core")
    implementation("io.dropwizard:dropwizard-jdbi3")
    implementation("io.dropwizard:dropwizard-migrations")
    
    // Validation
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    
    // Metrics
    implementation("io.dropwizard.metrics:metrics-core:4.2.35")
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
    
    // Database
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    implementation("org.flywaydb:flyway-core:10.8.1")
    
    // Roku ECP Wrapper - TODO: Add when available in Maven Central
    // implementation("com.github.wseemann:roku-ecp-wrapper-kotlin:1.3.0")
    
    // HTTP and networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // YAML
    implementation("org.yaml:snakeyaml:2.2")
    
    // Testing
    testImplementation("io.dropwizard:dropwizard-testing")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
    
    // JUnit Platform
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf("-Xjsr305=strict", "-opt-in=kotlin.RequiresOptIn")
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Main-Class" to "com.blockbuster.BlockbusterApplication",
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version
            )
        )
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
}
