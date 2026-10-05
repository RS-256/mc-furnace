plugins {
    id("java")
    id("application")
}

group = "net.rs256"
version = "0.1.0"

repositories {
    mavenCentral()
    maven {
        name = "FabricMC"
        url = uri("https://maven.fabricmc.net/")
    }
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(libs.picocli)
    implementation(libs.gson)
    implementation(libs.snakeyaml)
    implementation(libs.mapping.io)
    implementation(libs.tiny.remapper)
    implementation(libs.stitch)
    // Vineflower is launched as a separate process; the dependency is
    // declared only so its jar is placed into the distribution's lib directory.
    implementation(libs.vineflower)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

val datagenLauncher = sourceSets.create("datagenLauncher")

tasks.named<JavaCompile>(datagenLauncher.compileJavaTaskName) {
    options.release = 8
    options.compilerArgs.add("-Xlint:-options")
}

application {
    applicationName = "furnace"
    mainClass = "net.rs256.furnace.Furnace"
}

// Embed the pipeline git commit SHA so it can be recorded in version.json.
val generateBuildInfo = tasks.register("generateBuildInfo") {
    val outDir = layout.buildDirectory.dir("generated/buildinfo")
    outputs.dir(outDir)
    outputs.upToDateWhen { false }
    doLast {
        val sha = try {
            val proc = ProcessBuilder("git", "rev-parse", "HEAD")
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            proc.inputStream.bufferedReader().readText().trim().takeIf { proc.waitFor() == 0 }
        } catch (e: Exception) {
            null
        } ?: "unknown"
        val dirty = try {
            val proc = ProcessBuilder("git", "status", "--porcelain")
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor() == 0 && out.isNotBlank()
        } catch (e: Exception) {
            false
        }
        val dir = outDir.get().asFile
        dir.mkdirs()
        dir.resolve("furnace-build.properties").writeText(
            "pipelineCommit=$sha${if (dirty) "-dirty" else ""}\n" +
                "furnaceVersion=$version\n"
        )
    }
}

sourceSets.main {
    resources.srcDir(generateBuildInfo)
    runtimeClasspath += datagenLauncher.output
}

sourceSets.test {
    runtimeClasspath += datagenLauncher.output
}

tasks.jar {
    from(datagenLauncher.output)
}

tasks.test {
    useJUnitPlatform()
}
