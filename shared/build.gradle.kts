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

val llamatikWithLinuxNativesOnly = tasks.register<Jar>("llamatikWithLinuxNativesOnly") {
    description = "Repacks the llamatik JVM artifact, keeping only its Linux native libraries"
    archiveFileName.set("llamatik-linux-natives-only.jar")
    destinationDirectory.set(layout.buildDirectory.dir("llamatik"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(llamatikJvmArtifact.elements.map { artifacts -> artifacts.map { zipTree(it.asFile) } }) {
        exclude("native/macos/**")
        exclude("native/windows/**")
        exclude("META-INF/MANIFEST.MF")
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
                implementation(libs.icons.lucide)
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
                implementation(libs.zxing.core)
                implementation(libs.java.keyring)
                implementation(libs.pdfbox)
                implementation(libs.sqldelight.sqlite.driver)
                implementation(libs.dbus.java.core)
                implementation(libs.dbus.java.transport.native.unixsocket)

                runtimeOnly(files(llamatikWithLinuxNativesOnly))
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
}

val desktopMainClass = "com.emberr.DesktopMainKt"
val desktopWindowClassName = desktopMainClass.replace('.', '-')
val applicationName = "Emberr"
val applicationVersion = "1.0.0"
val packageIdentifier = "emberr"
val packageRelease = "1"
val menuCategory = "Office"
val licenseType = "AGPL-3.0-or-later"
val linuxIconFile = layout.projectDirectory.file("packaging/linux/emberr.png")
val linuxPackagingTemplateDir = layout.projectDirectory.dir("packaging/linux/jpackage")
val linuxPackagingResourceDir = layout.buildDirectory.dir("compose/packaging/linux")

compose.desktop {
    application {
        mainClass = desktopMainClass
        javaHome = desktopRuntimeJdk.get().metadata.installationPath.asFile.absolutePath

        jvmArgs += listOf(
            "-Xmx1g",
            "-XX:MaxMetaspaceSize=256m",
            "-XX:+UseG1GC",
            "-XX:G1PeriodicGCInterval=30000",
            "-XX:MaxHeapFreeRatio=30",
            "-XX:MinHeapFreeRatio=10"
        )

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm
            )
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
                appCategory = menuCategory
                menuGroup = menuCategory
                shortcut = true
                iconFile.set(linuxIconFile)
            }
        }
    }
}

// Linux release packaging
//
// Build the RPM users install with:
//
//     ./gradlew :shared:packageRpmWithDesktopEntry
//
// Result: shared/build/compose/binaries/main/rpm/emberr-<version>-<release>.x86_64.rpm
// Bump applicationVersion above for a new release; bump packageRelease only when
// repackaging the same app version.
//
// ./gradlew packageRpm and ./gradlew :shared:packageRpm also work and build the exact
// same file, because the block below redirects them here. They report SKIPPED, which is
// expected, not a failure.
//
// Compose's own packageRpm cannot be used directly: it always passes its own
// --resource-dir and wipes that folder, so the Emberr.desktop and emberr.spec overrides
// in packaging/linux/jpackage never reach jpackage. Without them the window has no
// matching desktop entry and file associations break. That is why jpackage is invoked
// by hand below, against the app image that createDistributable produces.
val prepareLinuxPackagingResources = tasks.register<Sync>("prepareLinuxPackagingResources") {
    group = "compose desktop"
    description = "Fills in the desktop entry template with the window class the AWT toolkit reports."
    from(linuxPackagingTemplateDir)
    into(linuxPackagingResourceDir)
    filesMatching("*.desktop") {
        filter { line -> line.replace("@WINDOW_CLASS@", desktopWindowClassName) }
    }
}

val packageRpmWithDesktopEntry = tasks.register<Exec>("packageRpmWithDesktopEntry") {
    group = "compose desktop"
    description = "Builds the RPM with a desktop entry the desktop shell can match to the app window."
    dependsOn("createDistributable", prepareLinuxPackagingResources)

    val appImageDir = layout.buildDirectory.dir("compose/binaries/main/app/$applicationName")
    val rpmOutputDir = layout.buildDirectory.dir("compose/binaries/main/rpm")

    inputs.dir(appImageDir).withPropertyName("appImage")
    inputs.dir(linuxPackagingResourceDir).withPropertyName("packagingResources")
    inputs.file(linuxIconFile).withPropertyName("icon")
    outputs.dir(rpmOutputDir).withPropertyName("rpmOutput")

    doFirst {
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

// Both stock Compose RPM tasks are disabled and point at the task above, so no command
// can ship an RPM that is missing the desktop entry overrides.
val stockRpmTaskNames = setOf("packageRpm", "packageReleaseRpm")

tasks.matching { it.name in stockRpmTaskNames }.configureEach {
    dependsOn(packageRpmWithDesktopEntry)
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