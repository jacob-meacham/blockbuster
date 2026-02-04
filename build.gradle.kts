import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    id("com.gradleup.shadow") version "9.0.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
    id("org.jmailen.kotlinter") version "4.2.0"
    jacoco
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
    
    // Database
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
    implementation("org.flywaydb:flyway-core:10.8.1")
    implementation("com.zaxxer:HikariCP:5.1.0")
    
    // Roku ECP Wrapper - TODO: Add when available in Maven Central
    // implementation("com.github.wseemann:roku-ecp-wrapper-kotlin:1.3.0")
    
    // HTTP and networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    
    // Testing
    testImplementation("io.dropwizard:dropwizard-testing")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    
    // JUnit Platform
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf("-Xjsr305=strict", "-opt-in=kotlin.RequiresOptIn")
    }
}

detekt {
    config.setFrom(files("detekt.yml"))
    buildUponDefaultConfig = true
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn("jacocoTestCoverageVerification")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
        afterSuite(KotlinClosure2<TestDescriptor, TestResult, Unit>({ desc, result ->
            if (desc.parent == null) {
                println("\nBackend: ${result.testCount} tests, ${result.successfulTestCount} passed, ${result.failedTestCount} failed, ${result.skippedTestCount} skipped")
            }
        }))
    }
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
    dependsOn("copyFrontendDist")
}

tasks.register<Exec>("buildFrontend") {
    group = "build"
    description = "Build the frontend with Vite"
    workingDir = file("frontend")
    commandLine("npm", "run", "build")
    // Only re-run if frontend sources changed
    inputs.dir("frontend/src")
    inputs.file("frontend/package.json")
    inputs.file("frontend/index.html")
    inputs.file("frontend/vite.config.ts")
    outputs.dir("frontend/dist")
}

tasks.register<Copy>("copyFrontendDist") {
    group = "build"
    description = "Copy frontend build output into backend classpath resources"
    dependsOn("buildFrontend")
    from("frontend/dist")
    into(layout.buildDirectory.dir("resources/main/frontend"))
}

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Run the Blockbuster application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.blockbuster.BlockbusterApplication")

    // Pass through command line arguments via -Pargs="..."
    if (project.hasProperty("args")) {
        args(project.property("args").toString().split(" "))
    }
}
