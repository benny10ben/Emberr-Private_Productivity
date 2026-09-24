import java.io.File
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.cash.sqldelight)
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "${projectDir}/schemas")
}

val javaToolchainVersion = libs.versions.javaToolchain.get()
val kotlinStdlibVersion = libs.versions.kotlin.get()

val llamatikJvmArtifact: Configuration = configurations.create("llamatikJvmArtifact") {
    isTransitive = false
}

dependencies {
    llamatikJvmArtifact(libs.llamatik.library.jvm)
}

val hostOperatingSystemName = System.getProperty("os.name").orEmpty().lowercase()

val isBuildingOnWindows = hostOperatingSystemName.contains("win")

val llamatikNativeDirectoryNames = listOf("linux", "windows", "macos")

val llamatikNativeDirectoryForHost = when {
    hostOperatingSystemName.contains("linux") -> "linux"
    isBuildingOnWindows -> "windows"
    hostOperatingSystemName.contains("mac") -> "macos"
    else -> null
}

val llamatikWithHostNativesOnly = tasks.register<Jar>("llamatikWithHostNativesOnly") {
    description = "Repacks the llamatik JVM artifact, keeping only the native libraries for the operating system building the app"
    archiveFileName.set("llamatik-host-natives-only.jar")
    destinationDirectory.set(layout.buildDirectory.dir("llamatik"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(llamatikJvmArtifact.elements.map { artifacts -> artifacts.map { zipTree(it.asFile) } }) {
        exclude("META-INF/MANIFEST.MF")
        llamatikNativeDirectoryNames
            .filterNot { directoryName -> directoryName == llamatikNativeDirectoryForHost }
            .forEach { directoryName -> exclude("native/$directoryName/**") }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=coil3.annotation.ExperimentalCoilApi",
            "-Xexpect-actual-classes"
        )
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaToolchainVersion))
    }
}

tasks.matching { it.name.contains("AndroidHostTest") && it.name.contains("Lint", ignoreCase = true) }.configureEach {
    enabled = false
}

kotlin {
    jvmToolchain(javaToolchainVersion.toInt())

    androidLibrary {
        namespace = "com.emberr"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()

        androidResources {
            enable = true
        }

        withHostTest {
            isReturnDefaultValues = true
        }
    }

    jvm("desktop") {
        mainRun {
            mainClass.set("com.emberr.DesktopMainKt")
        }
    }

    sourceSets {
        // Android and desktop are both JVM, so file-based code that is not Android-specific lives
        // here instead of being written twice.
        val jvmSharedMain = create("jvmSharedMain") {
            dependsOn(getByName("commonMain"))
        }

        getByName("commonMain") {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.materialIconsExtended)

                implementation(libs.koin.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.lifecycle.runtime.compose)
                implementation(libs.androidx.room.runtime)
                implementation(libs.haze)
                implementation(libs.koin.compose.multiplatform)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.kotlinx.datetime)
                implementation(libs.coil.compose)
                implementation(libs.navigation.compose.kmp)
                implementation(libs.coil.network.ktor3)
                implementation(libs.androidx.sqlite.bundled)
                implementation(libs.okio)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.auth)
                implementation(libs.ktor.serialization.kotlinx.json)

                implementation(libs.llamatik.library)
                implementation(libs.sqldelight.coroutines.extensions)
                implementation(libs.kotlinx.collections.immutable)
            }
        }

        getByName("commonTest") {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jvmSharedTest = create("jvmSharedTest") {
            dependsOn(getByName("commonTest"))
        }

        getByName("androidHostTest") {
            dependsOn(jvmSharedTest)
        }

        getByName("desktopTest") {
            dependsOn(jvmSharedTest)
            dependencies {
                implementation(libs.androidx.room.testing)
            }
        }

        getByName("androidMain") {
            dependsOn(jvmSharedMain)
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.lifecycle.runtime.ktx)
                implementation(libs.androidx.lifecycle.process)
                implementation(libs.koin.android)
                implementation(libs.koin.androidx.compose)
                implementation(libs.androidx.room.ktx)
                implementation(libs.sqlcipher)
                implementation(libs.androidx.sqlite.ktx)
                implementation(libs.tink.android)
                implementation(libs.androidx.glance.appwidget)
                implementation(libs.jsoup)
                implementation(libs.ktor.client.okhttp)
                implementation(libs.androidx.documentfile)
                implementation(libs.koin.androidx.workmanager)

                implementation(libs.androidx.camera.camera2)
                implementation(libs.androidx.camera.lifecycle)
                implementation(libs.androidx.camera.view)

                implementation(libs.mlkit.barcode.scanning)
                implementation(libs.guava)

                implementation(libs.androidx.core.splashscreen)
                implementation(libs.sqldelight.android.driver)
            }
        }

        getByName("desktopMain") {
            dependsOn(jvmSharedMain)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.jsoup)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ktor.client.java)
                implementation(libs.ktor.server.netty)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.server.auth)
                implementation(libs.jmdns)
                implementation(libs.jna.platform)
                implementation(libs.zxing.core)
                implementation(libs.java.keyring)
                implementation(libs.pdfbox)
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.dbus.java.core)
                implementation(libs.dbus.java.transport.native.unixsocket)

                runtimeOnly(files(llamatikWithHostNativesOnly))
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("user.timezone", "UTC")
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")

    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

configurations.named("desktopRuntimeClasspath") {
    exclude(mapOf("group" to "com.llamatik", "module" to "library-jvm"))
}

val desktopRuntimeJdk = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(javaToolchainVersion.toInt()))
    if (!isBuildingOnWindows) {
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
}

val desktopMainClass = "com.emberr.DesktopMainKt"
val desktopWindowClassName = desktopMainClass.replace('.', '-')
val applicationName = "Emberr"
val applicationVersion = libs.versions.appVersion.get()
val packageIdentifier = "emberr"
val packageRelease = "1"
val menuCategory = "Office"
val licenseType = "AGPL-3.0-or-later"
val redHatBuildTools = listOf("rpmbuild")
val redHatBuildToolInstallHint = "sudo dnf install rpm-build"
val debianSection = "utils"
val debianMaintainerEmail = "developer.ben10@gmail.com"
val debianRequiredSystemPackages = listOf(
    "libc6",
    "libstdc++6",
    "libgcc-s1",
    "libgl1",
    "libx11-6",
    "libxext6",
    "libxrender1",
    "libxtst6",
    "libxi6",
    "libfontconfig1",
    "libfreetype6",
    "libasound2",
    "xdg-utils"
)
val debianBuildTools = listOf("dpkg", "dpkg-deb", "fakeroot")
val debianBuildToolInstallHint = "sudo dnf install dpkg fakeroot"
val linuxIconFile = layout.projectDirectory.file("packaging/linux/emberr.png")
val linuxPackagingTemplateDir = layout.projectDirectory.dir("packaging/linux/jpackage")
val linuxPackagingResourceDir = layout.buildDirectory.dir("compose/packaging/linux")
val tarballScriptTemplateDir = layout.projectDirectory.dir("packaging/linux/tarball")
val tarballRootDirectoryName = "$packageIdentifier-$applicationVersion-x86_64"
val installedIconSize = "512"
val appDirTemplateDir = layout.projectDirectory.dir("packaging/linux/appimage")
val appDirStagingDir = layout.buildDirectory.dir("compose/packaging/appdir")
val appImageBuildTools = listOf("appimagetool", "mksquashfs")
val appImageBuildToolInstallHint =
    "sudo dnf install squashfs-tools, and put appimagetool from " +
        "https://github.com/AppImage/appimagetool/releases on your PATH"
val windowsIconFile = layout.projectDirectory.file("packaging/windows/emberr.ico")
val windowsStartMenuGroup = applicationName
val windowsUpgradeUuid = "66e9f5e4-6a45-45bb-bef5-bed25991c933"

compose.desktop {
    application {
        mainClass = desktopMainClass
        javaHome = desktopRuntimeJdk.get().metadata.installationPath.asFile.absolutePath

        jvmArgs += listOf(
            "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
            "--add-opens", "java.desktop/sun.awt.X11=ALL-UNNAMED",
            "-Xmx512m",
            "-XX:MaxMetaspaceSize=256m",
            "-XX:+UseG1GC",
            "-XX:G1PeriodicGCInterval=30000",
            "-XX:MaxHeapFreeRatio=30",
            "-XX:MinHeapFreeRatio=10",
            "-XX:ParallelGCThreads=4",
            "-XX:ConcGCThreads=1",
            "-XX:G1ConcRefinementThreads=4",
            "-XX:CICompilerCount=2"
        )

        if (isBuildingOnWindows) {
            jvmArgs += listOf(
                "-XX:+AutoCreateSharedArchive",
                "-XX:SharedArchiveFile=\$APPDIR/startup-classes.jsa"
            )
        }

        nativeDistributions {
            packageName = applicationName
            packageVersion = applicationVersion

            modules(
                "java.sql",
                "java.prefs",
                "java.net.http",
                "java.naming",
                "java.management",
                "java.instrument",
                "jdk.unsupported",
                "jdk.crypto.cryptoki",
                "jdk.security.auth",
                "jdk.net"
            )

            linux {
                packageName = packageIdentifier
                packageVersion = applicationVersion
                appRelease = packageRelease
                rpmLicenseType = licenseType
                debMaintainer = debianMaintainerEmail
                appCategory = menuCategory
                menuGroup = menuCategory
                shortcut = true
                iconFile.set(linuxIconFile)
            }

            windows {
                packageVersion = applicationVersion
                iconFile.set(windowsIconFile)
                menu = true
                menuGroup = windowsStartMenuGroup
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = windowsUpgradeUuid
            }

            if (isBuildingOnWindows) {
                targetFormats(TargetFormat.Exe)
            }
        }
    }
}

// Linux release packaging
//
// Status: the formats meant for release are tarball, AppImage and Flatpak, and none of
// them are built here yet. RPM and DEB below are complete but dormant, kept for later.
// All of them, including the three not written yet, wrap the same app image that
// createDistributable produces, so that task is the one piece none of this works without.
//
// targetFormats stays empty on Linux, so Compose registers no packaging tasks of its own
// here and packageDistributionForCurrentOS does nothing. The RPM and DEB tasks below call
// jpackage by hand and only run when named explicitly. An empty set is safe: it is the
// value Compose itself starts from, and createDistributable, run and runDistributable
// never read it. Note that targetFormats() rejects an empty argument list, so a format is
// removed by deleting the call, not by calling it with no formats.
//
// Windows is the one exception. TargetFormat.Exe is requested in the block above, but
// only while the build itself is running on Windows. Compose registers a task for every
// requested format on every operating system and merely disables the ones the host cannot
// build, so asking for Exe unconditionally would leave two dead packageExe tasks sitting
// in the Linux task list. jpackage cannot cross-compile, so the installer has to be built
// from Windows regardless:
//
//     gradlew :shared:packageExe
//
// Result: shared\build\compose\binaries\main\exe\Emberr-<version>.exe
//
// WiX needs no manual install. Compose downloads WiX 3.11.2 into
// <gradle user home>\compose-jb\wix311 on first use, unless WIX_PATH already points at a
// toolset directory or compose.desktop.application.downloadWix=false turns that off.
// The Windows build does need the Android SDK, because the shared module applies the
// Android library plugin: set ANDROID_HOME, or put sdk.dir in local.properties, which is
// git ignored and so never arrives with a fresh clone.
//
// Build the RPM with:
//
//     ./gradlew :shared:packageRpmWithDesktopEntry
//
// Result: shared/build/compose/binaries/main/rpm/emberr-<version>-<release>.x86_64.rpm
// Bump appVersion in gradle/libs.versions.toml for a new release; bump packageRelease only when
// repackaging the same app version.
//
// Compose's own packageRpm could not be used even when it was registered: it always
// passes its own --resource-dir and wipes that folder, so the Emberr.desktop and
// emberr.spec overrides in packaging/linux/jpackage never reach jpackage. Without them
// the window has no matching desktop entry and file associations break. That is why
// jpackage is invoked by hand below, against the app image createDistributable produces.
//
// Before shipping an RPM or a DEB to users, add a license: pass --license-file with the
// repository LICENSE to both tasks. jpackage warns that packages without one look low
// quality, and lintian reports a DEB that installs no copyright file.
val addStartupClassCacheToAppImage = tasks.register<Exec>("addStartupClassCacheToAppImage") {
    group = "desktop packaging"
    description = "Dumps the shared class archive jlink leaves out, so launches skip reloading every JDK class."
    dependsOn("createDistributable")

    val bundledRuntimeFolderName = if (isBuildingOnWindows) "runtime" else "lib/runtime"
    val launcherFileName = if (isBuildingOnWindows) "java.exe" else "java"
    val appImageDirectory = layout.buildDirectory.dir("compose/binaries/main/app/$applicationName")
    val fullJdkLauncherFile = desktopRuntimeJdk.get().metadata.installationPath
        .file("bin/$launcherFileName").asFile
    val borrowedLauncherFile = appImageDirectory.get().asFile
        .resolve("$bundledRuntimeFolderName/bin/$launcherFileName")

    outputs.upToDateWhen { false }

    doFirst {
        borrowedLauncherFile.parentFile.mkdirs()
        fullJdkLauncherFile.copyTo(borrowedLauncherFile, overwrite = true)
        borrowedLauncherFile.setExecutable(true)
    }

    commandLine(borrowedLauncherFile.absolutePath, "-Xshare:dump")

    doLast {
        borrowedLauncherFile.delete()
        val borrowedLauncherDirectory = borrowedLauncherFile.parentFile
        if (borrowedLauncherDirectory.list()?.isEmpty() == true) {
            borrowedLauncherDirectory.delete()
        }
    }
}

val windowsPackagingTaskNames = setOf("packageExe", "packageReleaseExe", "packageMsi", "packageReleaseMsi")

tasks.matching { it.name in windowsPackagingTaskNames }.configureEach {
    dependsOn(addStartupClassCacheToAppImage)
}

val prepareLinuxPackagingResources = tasks.register<Sync>("prepareLinuxPackagingResources") {
    group = "linux packaging"
    description = "Fills in the desktop entry template with the window class the AWT toolkit reports."
    from(linuxPackagingTemplateDir)
    into(linuxPackagingResourceDir)
    filesMatching("*.desktop") {
        filter { line -> line.replace("@WINDOW_CLASS@", desktopWindowClassName) }
    }
}

tasks.register<Exec>("packageRpmWithDesktopEntry") {
    group = "linux packaging"
    description = "Builds the RPM with a desktop entry the desktop shell can match to the app window."
    dependsOn("createDistributable", prepareLinuxPackagingResources, addStartupClassCacheToAppImage)

    val appImageDir = layout.buildDirectory.dir("compose/binaries/main/app/$applicationName")
    val rpmOutputDir = layout.buildDirectory.dir("compose/binaries/main/rpm")
    val requiredBuildTools = redHatBuildTools
    val buildToolInstallHint = redHatBuildToolInstallHint

    inputs.dir(appImageDir).withPropertyName("appImage")
    inputs.dir(linuxPackagingResourceDir).withPropertyName("packagingResources")
    inputs.file(linuxIconFile).withPropertyName("icon")
    outputs.dir(rpmOutputDir).withPropertyName("rpmOutput")

    doFirst {
        val pathDirectories = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        val missingTools = requiredBuildTools.filter { toolName ->
            pathDirectories.none { directory -> File(directory, toolName).canExecute() }
        }
        if (missingTools.isNotEmpty()) {
            throw GradleException(
                "Cannot build the RPM because these tools are not on PATH: " +
                    missingTools.joinToString(", ") +
                    ". Install them with: $buildToolInstallHint"
            )
        }
        val outputDirectory = rpmOutputDir.get().asFile
        outputDirectory.deleteRecursively()
        outputDirectory.mkdirs()
    }

    commandLine(
        desktopRuntimeJdk.get().metadata.installationPath.file("bin/jpackage").asFile.absolutePath,
        "--type", "rpm",
        "--app-image", appImageDir.get().asFile.absolutePath,
        "--resource-dir", linuxPackagingResourceDir.get().asFile.absolutePath,
        "--icon", linuxIconFile.asFile.absolutePath,
        "--dest", rpmOutputDir.get().asFile.absolutePath,
        "--name", applicationName,
        "--app-version", applicationVersion,
        "--linux-package-name", packageIdentifier,
        "--linux-app-release", packageRelease,
        "--linux-app-category", menuCategory,
        "--linux-menu-group", menuCategory,
        "--linux-rpm-license-type", licenseType,
        "--linux-shortcut"
    )
}

// Debian release packaging
//
// Not released yet. Nothing depends on the task below, so it only runs when it is named
// explicitly:
//
//     ./gradlew :shared:packageDebWithDesktopEntry
//
// Result: shared/build/compose/binaries/main/deb/emberr_<version>-<release>_amd64.deb
//
// Before the first run, install the Debian tooling Fedora does not ship by default:
//
//     sudo dnf install dpkg fakeroot
//
// Three Debian specific settings are passed by hand because jpackage cannot work them
// out while it runs on Fedora:
//
//   1. --linux-package-deps. jpackage only fills in Depends: when it detects a Debian
//      host, which it does by running "dpkg -s coreutils". Fedora's dpkg ships an empty
//      package database, so that check fails and the dependency list comes out blank.
//      debianRequiredSystemPackages above is the hand-maintained replacement, taken from
//      the NEEDED entries of every native library the app image loads.
//   2. --linux-app-category. This fills in Section:, which Debian expects to be one of
//      its own section names, so it is "utils" and not the "Office" the RPM uses.
//      The desktop entry category stays "Office" because that comes from the separate
//      --linux-menu-group option.
//   3. --linux-deb-maintainer. Without it the Maintainer: field reads "Unknown".
tasks.register<Exec>("packageDebWithDesktopEntry") {
    group = "linux packaging"
    description = "Builds the DEB with a desktop entry, an explicit dependency list and Debian metadata."
    dependsOn("createDistributable", prepareLinuxPackagingResources, addStartupClassCacheToAppImage)

    val appImageDir = layout.buildDirectory.dir("compose/binaries/main/app/$applicationName")
    val debOutputDir = layout.buildDirectory.dir("compose/binaries/main/deb")
    val requiredBuildTools = debianBuildTools
    val buildToolInstallHint = debianBuildToolInstallHint

    inputs.dir(appImageDir).withPropertyName("appImage")
    inputs.dir(linuxPackagingResourceDir).withPropertyName("packagingResources")
    inputs.file(linuxIconFile).withPropertyName("icon")
    outputs.dir(debOutputDir).withPropertyName("debOutput")

    doFirst {
        val pathDirectories = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        val missingTools = requiredBuildTools.filter { toolName ->
            pathDirectories.none { directory -> File(directory, toolName).canExecute() }
        }
        if (missingTools.isNotEmpty()) {
            throw GradleException(
                "Cannot build the DEB because these tools are not on PATH: " +
                    missingTools.joinToString(", ") +
                    ". Install them with: $buildToolInstallHint"
            )
        }
        val outputDirectory = debOutputDir.get().asFile
        outputDirectory.deleteRecursively()
        outputDirectory.mkdirs()
    }

    commandLine(
        desktopRuntimeJdk.get().metadata.installationPath.file("bin/jpackage").asFile.absolutePath,
        "--type", "deb",
        "--app-image", appImageDir.get().asFile.absolutePath,
        "--resource-dir", linuxPackagingResourceDir.get().asFile.absolutePath,
        "--icon", linuxIconFile.asFile.absolutePath,
        "--dest", debOutputDir.get().asFile.absolutePath,
        "--name", applicationName,
        "--app-version", applicationVersion,
        "--linux-package-name", packageIdentifier,
        "--linux-app-release", packageRelease,
        "--linux-app-category", debianSection,
        "--linux-menu-group", menuCategory,
        "--linux-deb-maintainer", debianMaintainerEmail,
        "--linux-package-deps", debianRequiredSystemPackages.joinToString(", "),
        "--linux-shortcut"
    )
}

// Tarball release packaging
//
// This is the format meant to ship first. Build it with:
//
//     ./gradlew :shared:packageTarball
//
// Result: shared/build/compose/binaries/main/tarball/emberr-x86_64.tar.gz
//
// Unlike the RPM and DEB tasks this one needs no external tools at all, because a
// tarball is just the app image createDistributable already produced plus the two
// scripts below. jpackage is never involved, so there is no desktop entry template
// either: install.sh writes the entry itself, which it has to do because Exec must be
// the absolute path the user installed to and that is only known at install time.
//
// The scripts install per user and never ask for root. The icon goes to the hicolor
// theme rather than next to the desktop entry, because Icon=emberr is resolved through
// the icon theme and a loose file in applications/ would never be found.
tasks.register<Tar>("packageTarball") {
    group = "linux packaging"
    description = "Builds the user installable tarball with install.sh and uninstall.sh."
    dependsOn("createDistributable", addStartupClassCacheToAppImage)

    val appImageDir = layout.buildDirectory.dir("compose/binaries/main/app/$applicationName")

    archiveFileName.set("$packageIdentifier-x86_64.tar.gz")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/tarball"))
    compression = Compression.GZIP

    from(appImageDir) {
        into("$tarballRootDirectoryName/$packageIdentifier")
    }

    from(linuxIconFile) {
        into(tarballRootDirectoryName)
        rename { "$packageIdentifier.png" }
    }

    from(tarballScriptTemplateDir) {
        into(tarballRootDirectoryName)
        filePermissions { unix("0755") }
        filter { line ->
            line.replace("@APP_NAME@", applicationName)
                .replace("@PACKAGE_NAME@", packageIdentifier)
                .replace("@APP_VERSION@", applicationVersion)
                .replace("@WINDOW_CLASS@", desktopWindowClassName)
                .replace("@MENU_CATEGORY@", menuCategory)
                .replace("@ICON_SIZE@", installedIconSize)
        }
    }
}

// AppImage release packaging
//
// Build it with:
//
//     ./gradlew :shared:packageAppImage
//
// Result: shared/build/compose/binaries/main/appimage/Emberr-x86_64.AppImage
//
// Needs squashfs-tools from dnf and appimagetool on PATH. appimagetool is not packaged
// by Fedora and is only published as an AppImage, so it is downloaded by hand once
// rather than pinned here.
//
// Two names collide in this area and are worth keeping straight. Compose's
// createDistributable produces an "app image", which is just an unpacked folder. An
// AppImage is the single file format built here, and the folder it is built from is
// called an AppDir. The AppDir below puts Compose's app image at usr/, so the launcher
// at usr/bin/Emberr still finds usr/lib beside it exactly as it does when unpacked.
//
// The desktop entry and icon are deliberately duplicated: appimagetool requires both at
// the AppDir root, while the copies under usr/share are what desktop integration tools
// read once a user installs the AppImage.
//
// The AppDir is wiped before every copy. jlink writes the runtime's legal files as mode
// 444, Sync reproduces that in the AppDir, and the next build then fails with
// "Permission denied" because it cannot overwrite a read only file. Deleting first
// avoids that. Relaxing permissions per file is not an option: Gradle initialises
// FileCopyDetails.permissions from its own default rather than from the source file, so
// touching it would drop the executable bit from the launcher and every .so.
val prepareAppDir = tasks.register<Sync>("prepareAppDir") {
    group = "linux packaging"
    description = "Lays out the AppDir that appimagetool turns into a single AppImage file."
    dependsOn("createDistributable", addStartupClassCacheToAppImage)

    val composeAppImageDir = layout.buildDirectory.dir("compose/binaries/main/app/$applicationName")
    val iconThemeDirectory = "usr/share/icons/hicolor/${installedIconSize}x${installedIconSize}/apps"
    val stagingDirectory = appDirStagingDir

    into(appDirStagingDir)

    doFirst {
        stagingDirectory.get().asFile.deleteRecursively()
    }

    from(composeAppImageDir) {
        into("usr")
    }

    from(linuxIconFile) {
        rename { "$packageIdentifier.png" }
    }

    from(linuxIconFile) {
        into(iconThemeDirectory)
        rename { "$packageIdentifier.png" }
    }

    from(appDirTemplateDir) {
        filePermissions { unix("0755") }
        filter { line ->
            line.replace("@APP_NAME@", applicationName)
                .replace("@PACKAGE_NAME@", packageIdentifier)
                .replace("@MENU_CATEGORY@", menuCategory)
                .replace("@WINDOW_CLASS@", desktopWindowClassName)
        }
    }

    from(appDirTemplateDir) {
        include("*.desktop")
        into("usr/share/applications")
        filter { line ->
            line.replace("@APP_NAME@", applicationName)
                .replace("@PACKAGE_NAME@", packageIdentifier)
                .replace("@MENU_CATEGORY@", menuCategory)
                .replace("@WINDOW_CLASS@", desktopWindowClassName)
        }
    }
}

tasks.register<Exec>("packageAppImage") {
    group = "linux packaging"
    description = "Builds the single file AppImage users can download and run without installing."
    dependsOn(prepareAppDir)

    val appImageOutputDir = layout.buildDirectory.dir("compose/binaries/main/appimage")
    val appImageFileName = "$applicationName-x86_64.AppImage"
    val requiredBuildTools = appImageBuildTools
    val buildToolInstallHint = appImageBuildToolInstallHint

    inputs.dir(appDirStagingDir).withPropertyName("appDir")
    outputs.dir(appImageOutputDir).withPropertyName("appImageOutput")

    environment("ARCH", "x86_64")

    doFirst {
        val pathDirectories = System.getenv("PATH").orEmpty().split(File.pathSeparator)
        val missingTools = requiredBuildTools.filter { toolName ->
            pathDirectories.none { directory -> File(directory, toolName).canExecute() }
        }
        if (missingTools.isNotEmpty()) {
            throw GradleException(
                "Cannot build the AppImage because these tools are not on PATH: " +
                    missingTools.joinToString(", ") +
                    ". Install them with: $buildToolInstallHint"
            )
        }
        executable = pathDirectories
            .map { directory -> File(directory, "appimagetool") }
            .first { toolFile -> toolFile.canExecute() }
            .absolutePath
        val outputDirectory = appImageOutputDir.get().asFile
        outputDirectory.deleteRecursively()
        outputDirectory.mkdirs()
    }

    val pinnedRuntimeFile = providers.gradleProperty("appImageRuntimeFile").orNull

    commandLine(
        buildList {
            add("appimagetool")
            if (pinnedRuntimeFile != null) {
                add("--runtime-file")
                add(pinnedRuntimeFile)
            }
            add(appDirStagingDir.get().asFile.absolutePath)
            add(appImageOutputDir.get().asFile.resolve(appImageFileName).absolutePath)
        }
    )
}

// While targetFormats is empty Compose registers none of these, so this guard matches
// nothing today. It stays so that adding a format back cannot ship a package that is
// missing the desktop entry overrides. It disables rather than redirects, so no format
// can ever be dragged into a release on a machine without the tools to build it.
val stockComposePackagingTaskNames = setOf(
    "packageRpm",
    "packageReleaseRpm",
    "packageDeb",
    "packageReleaseDeb"
)

tasks.matching { it.name in stockComposePackagingTaskNames }.configureEach {
    onlyIf { false }
}

compose.resources {
    packageOfResClass = "emberr.shared.generated.resources"
}

sqldelight {
    databases {
        create("EmberrDatabase") {
            packageName.set("com.emberr.database")
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", libs.androidx.room.compiler)
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)

    add("androidRuntimeClasspath", libs.androidx.compose.ui.tooling)
}

configurations.all {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib")) {
                useVersion(kotlinStdlibVersion)
            }
        }
    }
}