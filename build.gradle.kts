plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "com.denizenmc"

// Releases are built with -Prelease and named Lightning-0.2.2.jar.
// Every other build is a dev build named after its commit, like Lightning-0.2.2+07e2ffa.jar.
val release = "0.2.2"
val commit = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }.getOrElse("")
version = if (project.hasProperty("release") || commit.isEmpty()) release else "$release+$commit"
description = "Go beyond simple formatting with universal message customization, reusable components, and fully configurable commands."

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    // 1.21.10 is the minimum API version required for player head components.
    // Newer API features are detected at runtime.
    compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0")
    compileOnly("me.clip:placeholderapi:2.12.2")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }

    jar {
        archiveFileName.set("Lightning-${project.version}.jar")
    }

    runServer {
        minecraftVersion("1.21.10")
    }
}