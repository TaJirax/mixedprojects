plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

/*
 * The desktop's third YouTube engine.
 *
 * NewPipeExtractor is a JVM library, which is why the phone has had it since
 * before the desktop did — the Android shell is already a JVM. The desktop is
 * Python, so the extractor runs in a small helper of its own and answers in
 * the same JSON the Android adapter produces. Everything above that point in
 * the app is shared: the same stream picking, the same downloader, the same
 * progress and cancellation.
 *
 * A fat jar rather than a directory of dependencies, and a runtime built with
 * jlink beside it, so the desktop package carries a JVM the size of the parts
 * it uses instead of asking anyone to install Java.
 */
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.1")
    implementation("org.json:json:20240303")
}

application {
    mainClass.set("net.blueknight.newpipe.Main")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

tasks.shadowJar {
    archiveFileName.set("blueknight-newpipe.jar")
    // A duplicate META-INF entry from two dependencies is not worth failing a
    // build over, and the extractor pulls in several.
    mergeServiceFiles()
}
