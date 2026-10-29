plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "com.denizenmc"
version = "0.2.2-alpha.2"
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