import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

val appRoot = rootProject.projectDir.parentFile   // the spoty/ folder
val signingPropsFile = rootProject.file("keystore.properties")
val repositoryKeystore = rootProject.file("../../keystore/whitebooster.jks")
val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
val requestedAbi = providers.gradleProperty("targetAbi").orNull ?: "universal"
val packagedAbis = if (requestedAbi == "universal") {
    supportedAbis
} else {
    require(requestedAbi in supportedAbis) {
        "Unsupported targetAbi '$requestedAbi'. Use universal or one of ${supportedAbis.joinToString()}."
    }
    listOf(requestedAbi)
}

// The engine is not forked for Android: the same files that the Windows,
// Linux and macOS builds run are copied in as the Python source set, so a fix
// lands on every platform at once. Copying rather than pointing srcDir at the
// parent keeps instagram.py and the other standalone CLIs out of the APK.
val syncEngine by tasks.registering(Copy::class) {
    from(appRoot) {
        include("spotify_downloader.py", "blueknight_paths.py", "pyshell.py")
    }
    into(layout.projectDirectory.dir("src/main/python"))
}

// The interface is one HTML document plus its backdrop; assets/ carries the logo.
val syncWeb by tasks.registering(Copy::class) {
    from(appRoot.resolve("web")) { into("web") }
    from(appRoot.resolve("assets")) { into("assets") }
    into(layout.projectDirectory.dir("src/main/assets"))
}

tasks.named("preBuild") { dependsOn(syncEngine, syncWeb) }
tasks.configureEach {
    if (name.endsWith("PythonSources")) dependsOn(syncEngine)
}

android {
    namespace = "net.blueknight.downloader"
    compileSdk = 34

    defaultConfig {
        applicationId = "net.blueknight.downloader"
        minSdk = 24          // Android 7.0: the oldest API the WebView bridge needs
        targetSdk = 34
        versionCode = 60804
        versionName = "6.8.4"

        // A normal build is universal. CI also passes -PtargetAbi=<ABI> to
        // produce smaller architecture-specific APKs from the same sources.
        // Python 3.11 is deliberate: it is the newest Chaquopy runtime which
        // still supports the two 32-bit Android ABIs.
        ndk { abiFilters += packagedAbis }
    }

    signingConfigs {
        create("release") {
            // A keystore.properties next to this module signs a real release;
            // without one the build still produces an installable APK rather
            // than failing, which is what makes a fresh clone buildable.
            if (signingPropsFile.exists()) {
                val props = Properties().apply { signingPropsFile.inputStream().use(::load) }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            } else if (repositoryKeystore.exists()) {
                // This repository already uses this signer for its Android
                // releases. Reusing it gives downloader updates a stable cert;
                // private CI secrets still override it through the properties.
                storeFile = repositoryKeystore
                storePassword = "whitebooster"
                keyAlias = "whitebooster"
                keyPassword = "whitebooster"
            }
        }
    }

    buildTypes {
        release {
            // The Python engine is interpreted and the Kotlin shell is a few
            // hundred lines; shrinking saves little and risks stripping the
            // WebView bridge's reflected @JavascriptInterface methods.
            isMinifyEnabled = false
            signingConfig =
                if (signingPropsFile.exists() || repositoryKeystore.exists())
                    signingConfigs.getByName("release")
                else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs {
            // FFmpeg and FFprobe are executables shipped under jniLibs. They
            // must land on disk uncompressed for the app to exec them.
            useLegacyPackaging = true
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"

        pip {
            // The engines. yt-dlp, gallery-dl and streamlink are the same
            // packages the desktop build ships as frozen executables.
            install("yt-dlp")
            install("gallery-dl")
            install("streamlink")

            // Replacements for the desktop-only components, chosen because
            // each is pure Python or has a Chaquopy-built wheel:
            //   pikepdf (native qpdf)      -> pypdf, for the same validation
            //   Calibre  (desktop program) -> ebooklib, for EPUB
            //   LibreOffice (desktop)      -> python-docx + odfpy + reportlab
            install("pypdf")
            install("ebooklib")
            install("python-docx")
            install("openpyxl")
            install("python-pptx")
            install("odfpy")
            install("reportlab")

            // spotDL cannot be installed: it needs pydantic-core and rapidfuzz,
            // both Rust/C++ extensions with no Android wheel. These are what
            // the Spotify path is rebuilt from instead.
            install("ytmusicapi")
            install("mutagen")

            install("Pillow")
            install("requests")
            install("certifi")
            install("brotli")
        }
    }
    sourceSets { getByName("main") { srcDir("src/main/python") } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.webkit:webkit:1.9.0")
}
