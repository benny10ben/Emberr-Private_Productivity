import java.io.File
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
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

// Desktop packaging
//
// Releases are built by .github/workflows/release.yml when a v* tag is pushed. To release,
// bump appVersion in gradle/libs.versions.toml. Bump packageRelease only to repackage the
// same version as an RPM or DEB.
//
// targetFormats stays empty on Linux, so Compose registers no packaging tasks there. Exe is
// only requested on Windows, because jpackage can't build for another OS.
// Building the .exe locally needs the Android SDK (ANDROID_HOME or sdk.dir), because this
// module applies the Android plugin.
//
// RPM and DEB work but aren't released. They call jpackage by hand because Compose's own
// tasks replace the custom .desktop and .spec files in packaging/linux/jpackage. Add
// --license-file before ever shipping them.
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

// DEB (not released). Needs: sudo dnf install dpkg fakeroot
// jpackage can't detect three Debian values on Fedora, so they are passed by hand:
// the dependency list, the Debian section ("utils", not "Office") and the maintainer.
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

// Tarball: the app image plus install.sh and uninstall.sh, which install for the current
// user under ~/.local/share. install.sh writes the desktop entry itself, because it needs
// the install path.
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

// AppImage: needs squashfs-tools and appimagetool on PATH. CI pins both and passes the
// runtime with -PappImageRuntimeFile.
// The AppDir puts Compose's app image under usr/, so the launcher still finds usr/lib.
// The desktop entry and icon go both at the root (for appimagetool) and under usr/share
// (for desktop integration tools).
// The AppDir is deleted before each copy, because jlink's read-only files would make the
// next build fail. Changing permissions during the copy would drop the executable bits.
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

// Safety net: if RPM or DEB is ever added to targetFormats, Compose's own tasks stay off,
// so they can't ship without the custom desktop entry.
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

configurations.matching { it.name != "androidLintTool" }.configureEach {
    resolutionStrategy {
        eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib")) {
                useVersion(kotlinStdlibVersion)
            }
        }
    }
}