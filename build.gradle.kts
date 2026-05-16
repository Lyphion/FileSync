import xyz.jpenilla.resourcefactory.bukkit.BukkitPluginYaml
import xyz.jpenilla.resourcefactory.bukkit.Permission

plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("xyz.jpenilla.resource-factory-paper-convention") version "1.3.1"
}

group = "dev.lyphium"
version = "2.1.0"
description = "Synchronize files and folders between servers"

dependencies {
    paperweight.paperDevBundle("26.1.2.build.+")

    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")

    testCompileOnly("org.projectlombok:lombok:1.18.46")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.46")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    compileJava {
        options.release = 25
    }
}

paperPluginYaml {
    main = "dev.lyphium.filesync.FileSync"
    load = BukkitPluginYaml.PluginLoadOrder.POSTWORLD
    apiVersion = "26.1.2"
    author = "Lyphion"
    website = "https://github.com/Lyphion/FileSync"
    permissions {
        register("filesync.admin") {
            description = "Admin permission"
            default = Permission.Default.OP
        }
    }
}
