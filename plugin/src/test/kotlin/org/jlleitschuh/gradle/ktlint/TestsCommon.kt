package org.jlleitschuh.gradle.ktlint

import org.eclipse.jgit.lib.RepositoryBuilder
import org.intellij.lang.annotations.Language
import java.io.File

fun File.buildFile() = resolve("build.gradle")

fun File.ktlintBuildDir() = resolve("build/ktlint")

@Language("Groovy")
private fun pluginsBlockWithMainPluginAndKotlinPlugin(
    kotlinPluginId: String,
    kotlinVersion: String? = null
) =
    """
        plugins {
            id '$kotlinPluginId'${if (kotlinVersion != null) " version '$kotlinVersion'" else ""}
            id 'org.jlleitschuh.gradle.ktlint'
        }
    """.trimIndent()

fun File.defaultProjectSetup(kotlinVersion: String? = null) {
    kotlinPluginProjectSetup("org.jetbrains.kotlin.jvm", kotlinVersion)
}

fun File.kotlinPluginProjectSetup(
    kotlinPluginId: String,
    kotlinPluginVersion: String? = null
) {
    //language=Groovy
    buildFile().writeText(
        """
            ${pluginsBlockWithMainPluginAndKotlinPlugin(kotlinPluginId, kotlinPluginVersion)}

            repositories {
                gradlePluginPortal()
            }
        """.trimIndent()
    )
}

internal fun File.initGit(): File {
    val repo = RepositoryBuilder().setWorkTree(this).setMustExist(false).build()
    repo.create()
    return repo.directory
}

internal fun File.initGitWithoutHooksDir(): File {
    val repo = RepositoryBuilder().setWorkTree(this).setMustExist(false).build()
    repo.create()
    assert(repo.directory.resolve("hooks").delete())
    return repo.directory
}

private val snakeCaseFilenameRegex = "(?=[a-zA-Z-.]+\\Z)(?:\\A|(?<=/)|[_-])([a-zA-Z])".toRegex()

internal val CLEAN_SOURCE_FILE = "clean-source.kt".toPascalCaseFilename()
internal val FAIL_SOURCE_FILE = "fail-source.kt".toPascalCaseFilename()
internal val KOTLIN_SCRIPT_FILE = "kotlin-script.kts".toPascalCaseFilename()
internal val KOTLIN_SCRIPT_FAIL_FILE = "kotlin-script-fail.kts".toPascalCaseFilename()

internal fun String.toPascalCaseFilename(): String = replace(snakeCaseFilenameRegex) { it.groups[1]?.value?.toUpperCase() ?: "" }
