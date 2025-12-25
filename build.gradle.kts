plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

group = "dev.enthusia"
version = "1.1.1"
description = "ItemShops - Player chest shops plugin with guild integration"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }

    maven("https://jitpack.io") {
        name = "jitpack"
    }

    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/") {
        name = "placeholderapi"
    }

    maven("https://maven.enginehub.org/repo/") {
        name = "enginehub"
    }
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    // Vault API (for economy)
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    // WorldGuard (for region detection)
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9")

    // ARM-Guilds-Bridge (for guild integration)
    compileOnly(files("../ARM-Guilds-Bridge/build/libs/ARM-Guilds-Bridge-1.0.0.jar"))

    // LumaGuilds (for guild data)
    compileOnly(files("../bell-claims/build/libs/LumaGuilds-0.6.2.jar"))
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("${project.name}-${project.version}.jar")

        // Relocate dependencies if needed
        // relocate("com.example.dependency", "dev.enthusia.itemshops.libs.dependency")
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        filesMatching("plugin.yml") {
            expand(
                "version" to project.version,
                "description" to project.description
            )
        }
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}
