plugins {
    `java-library`
    idea
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.moddev)
}

// gradle.properties 中的 Mod 元数据与版本区间
val mod_id: String by project
val mod_group_id: String by project
val mod_name: String by project
val mod_license: String by project
val mod_version: String by project
val mod_authors: String by project
val mod_description: String by project
val minecraft_version_range: String by project
val neo_version_range: String by project
val loader_version_range: String by project
val geckolib_version_range: String by project
val jade_version_range: String by project

version = mod_version
group = mod_group_id

repositories {
    maven {
        name = "GeckoLib"
        url = uri("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
        content {
            includeGroup("software.bernie.geckolib")
        }
    }
    maven {
        name = "Kotlin for Forge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
    }
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
    }
}

base {
    archivesName = mod_id
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

neoForge {
    version = libs.versions.neoForge.get()

    parchment {
        mappingsVersion = libs.versions.parchmentMappings.get()
        minecraftVersion = libs.versions.parchmentMinecraft.get()
    }

    runs {
        register("client") {
            client()

            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        register("server") {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        register("gameTestServer") {
            type = "gameTestServer"
            systemProperty("neoforge.enabledGameTestNamespaces", mod_id)
        }

        register("data") {
            data()

            programArguments.addAll(
                "--mod", mod_id,
                "--all",
                "--output", file("src/generated/resources/").absolutePath,
                "--existing", file("src/main/resources/").absolutePath
            )
        }

        configureEach {
            systemProperty("forge.logging.markers", "REGISTRIES")

            // dev 运行统一使用仓库内 log4j.xml:静音 registry/mixin 等噪声,保留 debug.log 全量输出
            loggingConfigFile = rootProject.file("log4j.xml")
        }
    }


    mods {
        register(mod_id) {
            sourceSet(sourceSets.main.get())
        }
    }
}

val mainSourceSet = sourceSets.main.get()
mainSourceSet.resources.srcDir("src/generated/resources")

dependencies {
    implementation(libs.kotlinforforge)
    implementation(libs.geckolib)
    implementation(libs.jade)
}

val generateModMetadata = tasks.register("generateModMetadata", ProcessResources::class.java) {
    val replaceProperties = mapOf(
        "minecraft_version" to libs.versions.minecraft.get(),
        "minecraft_version_range" to minecraft_version_range,
        "neo_version" to libs.versions.neoForge.get(),
        "neo_version_range" to neo_version_range,
        "loader_version_range" to loader_version_range,
        "geckolib_version_range" to geckolib_version_range,
        "jade_version_range" to jade_version_range,
        "mod_id" to mod_id,
        "mod_name" to mod_name,
        "mod_license" to mod_license,
        "mod_version" to mod_version,
        "mod_authors" to mod_authors,
        "mod_description" to mod_description,
    )
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")
}

mainSourceSet.resources.srcDir(generateModMetadata)
neoForge.ideSyncTask(generateModMetadata)

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}

// GameTest 结构文件:NeoForge 从工作目录 gameteststructures/ 读取,构建时同步
val copyGameTestStructures = tasks.register<Copy>("copyGameTestStructures") {
    from("gameteststructures")
    into("run/gameteststructures")
}

tasks.matching { it.name == "runGameTestServer" }.configureEach {
    dependsOn(copyGameTestStructures)
}