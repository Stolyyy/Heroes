import java.util.Properties

plugins {
    `java-library`
    // Bundles GlowingEntities into the plugin jar so the server needs no extra plugin installed.
    // 8.3.x line: shadow 9.x requires Gradle 9, and the wrapper here is 8.14.2.
    id("com.gradleup.shadow") version "8.3.11"
}

repositories {
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }

    maven {
        url = uri("https://repo.citizensnpcs.co")
    }

    // GlowingEntities: Maven Central only carries up to 1.3.4, which predates 1.21.11 support.
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly(libs.io.papermc.paper.paper.api)
    compileOnly("net.citizensnpcs:citizens-main:2.0.35-SNAPSHOT") {
        exclude(group = "*", module = "*")
    }
    // Per-viewer entity glow. Bukkit's Entity#setGlowing is a broadcast metadata flag, so
    // teammate-only outlines are impossible through the API alone; this sends per-player
    // metadata + team packets. 1.4.10 is the release that names 1.21.11.
    implementation("com.github.SkytAsul:GlowingEntities:1.4.10")
    // LuckPerms context calculator (mapper permissions scoped to template worlds). compileOnly:
    // the server-side LuckPerms plugin provides the classes at runtime; registration is guarded.
    compileOnly("net.luckperms:api:5.4")
}

group = "me.stolyy"
version = "4.0.1b"
description = "Heroes"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

// paper-plugin.yml carries ${version} so the jar and the plugin can never disagree about the build.
tasks.processResources {
    val projectVersion = version.toString()
    inputs.property("version", projectVersion)
    filesMatching("paper-plugin.yml") { expand("version" to projectVersion) }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>() {
    options.encoding = "UTF-8"
}

// Only the shaded jar is deployable — the plain one has no GlowingEntities inside it. Keep the
// plain jar in build/libs so both can't race for the same filename in the plugins folder.
tasks.jar {
    archiveClassifier = "plain"
}

// Deploy target: `deployDir` in a gitignored local.properties (or -PdeployDir=...) drops the shaded
// jar straight into a server's plugins folder; without it the jar lands in build/libs like any build.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val deployDir: String? = (project.findProperty("deployDir") as String?) ?: localProps.getProperty("deployDir")

tasks.shadowJar {
    archiveClassifier = ""
    if (deployDir != null) destinationDirectory = File(deployDir)
    // Relocated so a future server-side GlowingEntities (or another plugin shading it) can't
    // collide with ours on the classpath.
    // Whole fr.skytasul namespace, not just glowingentities — it pulls in reflection-remapper
    // transitively, which would otherwise ship unrelocated and collide with any other plugin
    // shading the same helper.
    relocate("fr.skytasul", "me.stolyy.heroes.libs.skytasul")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
