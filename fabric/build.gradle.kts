import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.matthewprenger.cursegradle.CurseArtifact
import com.matthewprenger.cursegradle.CurseProject
import com.matthewprenger.cursegradle.CurseRelation
import com.matthewprenger.cursegradle.Options
import net.fabricmc.loom.task.RemapJarTask

val mavenUser: String by rootProject
val mavenPassword: String by rootProject

val minecraftVersion: String by rootProject
val fabricLoaderVersion: String by rootProject
val fabricVersion: String by rootProject
val modVersion: String by rootProject
val serverVersion: String by rootProject
val mavenGroup: String by rootProject
val curseProjectId: String by rootProject
val curseFabricRelease: String by rootProject
val curseDisplayVersion: String by rootProject
val curseSupportedVersions: String by rootProject


configurations {
    create("shadowCommon")
}

architectury {
    platformSetupLoomIde()
    fabric()
}

base {
    archivesBaseName = "plasmovoice"
}

project.group = mavenGroup
project.version = modVersion

dependencies {
    minecraft("com.mojang:minecraft:${minecraftVersion}")
    mappings(loom.officialMojangMappings())

    implementation(project(":api")) {
        isTransitive = false
    }
    "shadowCommon"(project(":api")) {
        isTransitive = false
    }

    compileOnly(project(":common", "dev")) {
        isTransitive = false
    }
    project.configurations.getByName("developmentFabric")(project(":common", "dev")) {
        isTransitive = false
    }
    "shadowCommon"(project(":common", "transformProductionFabric")) {
        isTransitive = false
    }

    modApi("net.fabricmc:fabric-loader:${fabricLoaderVersion}")
    modApi("net.fabricmc.fabric-api:fabric-api:${fabricVersion}")

    // Fabric API jar-in-jar
//    include("net.fabricmc.fabric-api:fabric-api-base:0.3.0+a02b446318")?.let { modImplementation(it) }
//    include("net.fabricmc.fabric-api:fabric-command-api-v1:1.1.3+5ab9934c18")?.let { modImplementation(it) }
//    include("net.fabricmc.fabric-api:fabric-key-binding-api-v1:1.0.4+cbda931818")?.let { modImplementation(it) }
//    include("net.fabricmc.fabric-api:fabric-lifecycle-events-v1:1.4.4+a02b446318")?.let { modImplementation(it) }
//    include("net.fabricmc.fabric-api:fabric-networking-api-v1:1.0.13+cbda931818")?.let { modImplementation(it) }
//    include("net.fabricmc.fabric-api:fabric-rendering-v1:1.9.0+7931163218")?.let { modImplementation(it) }
//    include("net.fabricmc.fabric-api:fabric-resource-loader-v0:0.4.8+a00e834b18")?.let { modImplementation(it) }

    // YAML for server config
    implementation("org.yaml:snakeyaml:1.29")
    "shadowCommon"("org.yaml:snakeyaml:1.29")

    // Opus
    implementation("su.plo.voice:opus:1.1.2")
    "shadowCommon"("su.plo.voice:opus:1.1.2")

    // RNNoise
    implementation("su.plo.voice:rnnoise:1.0.0")
    "shadowCommon"("su.plo.voice:rnnoise:1.0.0")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.20")
    annotationProcessor("org.projectlombok:lombok:1.18.20")
}

tasks {
    jar {
        classifier = "dev"
    }

    processResources {
        filesMatching("fabric.mod.json") {
            expand(
                mutableMapOf(
                    "server_version" to serverVersion,
                    "version" to modVersion,
                    "loader_version" to fabricLoaderVersion,
                    "fabric_version" to fabricVersion
                )
            )
        }
    }

    shadowJar {
        configurations = listOf(project.configurations.getByName("shadowCommon"))
        classifier = "dev-shadow"

        dependencies {
            exclude(dependency("net.java.dev.jna:jna"))
            exclude(dependency("org.slf4j:slf4j-api"))
        }
    }

    remapJar {
        input.set(shadowJar.get().archiveFile)
        dependsOn(getByName<ShadowJar>("shadowJar"))
        archiveBaseName.set("plasmovoice-fabric-${minecraftVersion}")
    }

    build {
        doLast {
            shadowJar.get().archiveFile.get().asFile.delete()
        }
    }
}

val remapJar = tasks.getByName<RemapJarTask>("remapJar")

publishing {
    repositories {
        maven {
            credentials {
                username = mavenUser
                password = mavenPassword
            }
            url = uri("https://repo.plo.su/public/")
        }
    }
    publications {
        register("fabric", MavenPublication::class) {
            artifact(remapJar)
        }
    }
}

curseforge {
    apiKey = if (file("${rootDir}/curseforge_key.txt").exists()) {
        file("${rootDir}/curseforge_key.txt").readText()
    } else {
        ""
    }

    project(closureOf<CurseProject> {
        id = curseProjectId
        changelog = file("${rootDir}/changelog.txt")
        releaseType = curseFabricRelease
        curseSupportedVersions.split(",").forEach {
            addGameVersion(it)
        }
        addGameVersion("Fabric")

        mainArtifact(
            file("${project.buildDir}/libs/${remapJar.archiveBaseName.get()}-${version}.jar"),
            closureOf<CurseArtifact> {
                displayName = "[Fabric ${curseDisplayVersion}] Plasmo Voice $version"

                relations(closureOf<CurseRelation> {
                    optionalDependency("sound-physics-fabric")
                    optionalDependency("sound-physics-remastered")
                })
            })
        afterEvaluate {
            uploadTask.dependsOn(remapJar)
        }
    })

    options(closureOf<Options> {
        forgeGradleIntegration = false
    })
}