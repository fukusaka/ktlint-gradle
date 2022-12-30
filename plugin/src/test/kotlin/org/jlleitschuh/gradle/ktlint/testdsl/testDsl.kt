package org.jlleitschuh.gradle.ktlint.testdsl

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import org.jlleitschuh.gradle.ktlint.AbstractPluginTest
import org.jlleitschuh.gradle.ktlint.CLEAN_SOURCE_FILE
import org.jlleitschuh.gradle.ktlint.FAIL_SOURCE_FILE
import org.jlleitschuh.gradle.ktlint.KOTLIN_SCRIPT_FAIL_FILE
import org.jlleitschuh.gradle.ktlint.KOTLIN_SCRIPT_FILE
import java.io.File

fun AbstractPluginTest.project(
    gradleVersion: GradleVersion,
    projectPath: File = projectRoot,
    projectSetup: (File) -> Unit = defaultProjectSetup(gradleVersion),
    test: TestProject.() -> Unit = {}
): TestProject {
    projectSetup(projectPath)

    val gradleRunner = GradleRunner.create()
        .withGradleVersion(gradleVersion.version)
        .withTestKitDir(sharedTestKitDir)
        .forwardOutput()
        .withProjectDir(projectPath)

    val testProject = TestProject(
        gradleRunner,
        gradleVersion,
        projectPath
    )

    testProject.test()
    return testProject
}

class TestProject(
    val gradleRunner: GradleRunner,
    val gradleVersion: GradleVersion,
    val projectPath: File
) {
    val buildGradle get() = projectPath.resolve("build.gradle")
    val settingsGradle get() = projectPath.resolve("settings.gradle")

    fun withCleanSources() {
        createSourceFile(
            "src/main/kotlin/$CLEAN_SOURCE_FILE",
            """
            |val foo = "bar"
            |
            """.trimMargin()
        )
    }

    fun withFailingSources() {
        createSourceFile(
            "src/main/kotlin/$FAIL_SOURCE_FILE",
            """
            |val  foo    =     "bar"
            |
            """.trimMargin()
        )
    }

    fun withCleanKotlinScript() {
        createSourceFile(
            KOTLIN_SCRIPT_FILE,
            """
            |println("zzz")
            |
            """.trimMargin()
        )
    }

    fun withFailingKotlinScript() {
        createSourceFile(
            KOTLIN_SCRIPT_FAIL_FILE,
            """
            |println("zzz")@
            |
            """.trimMargin()
                .replace('@', ' ')
        )
    }

    fun createSourceFile(
        sourceFilePath: String,
        contents: String
    ) {
        val sourceFile = projectPath.resolve(sourceFilePath)
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText(contents)
    }

    fun restoreFailingSources() {
        val sourceFile = projectPath.resolve("src/main/kotlin/$FAIL_SOURCE_FILE")
        sourceFile.delete()
        withFailingSources()
    }

    fun removeSourceFile(sourceFilePath: String) {
        val sourceFile = projectPath.resolve(sourceFilePath)
        sourceFile.delete()
    }
}

fun TestProject.build(
    vararg buildArguments: String,
    assertions: BuildResult.() -> Unit = {}
) {
    gradleRunner
        .withArguments(buildArguments.toList() + "--stacktrace")
        .build()
        .run { assertions() }
}

fun TestProject.buildAndFail(
    vararg buildArguments: String,
    assertions: BuildResult.() -> Unit = {}
) {
    gradleRunner
        .withArguments(buildArguments.toList() + "--stacktrace")
        .buildAndFail()
        .run { assertions() }
}

fun defaultProjectSetup(gradleVersion: GradleVersion): (File) -> Unit =
    projectSetup("jvm", gradleVersion)

private val GradleVersion.supportedKotlinVersion
    get() = if (this <= GradleVersion.version("6.1.1")) {
        TestVersions.minSupportedKotlinPluginVersion
    } else {
        TestVersions.maxSupportedKotlinPluginVersion
    }

fun projectSetup(
    kotlinPluginType: String,
    gradleVersion: GradleVersion,
): (File) -> Unit = {
    val kotlinPluginVersion = gradleVersion.supportedKotlinVersion
    //language=Groovy
    it.resolve("build.gradle").writeText(
        """
        |plugins {
        |    id 'org.jetbrains.kotlin.$kotlinPluginType'
        |    id 'org.jlleitschuh.gradle.ktlint'
        |}
        |
        |repositories {
        |    mavenCentral()
        |}
        |
        """.trimMargin()
    )

    //language=Groovy
    it.resolve("settings.gradle").writeText(
        """
        |pluginManagement {
        |    repositories {
        |        mavenLocal()
        |        gradlePluginPortal()
        |    }
        |
        |    plugins {
        |         id 'org.jetbrains.kotlin.$kotlinPluginType' version '$kotlinPluginVersion'
        |         id 'org.jlleitschuh.gradle.ktlint' version '${TestVersions.pluginVersion}'
        |    }
        |}
        |
        """.trimMargin()
    )
}

private val sharedTestKitDir = File(".")
    .resolve(".gradle-test-kit")
    .absoluteFile
    .also {
        if (!it.exists()) it.mkdir()
    }
