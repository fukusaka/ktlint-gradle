package org.jlleitschuh.gradle.ktlint

import org.jlleitschuh.gradle.ktlint.tasks.GenerateReportsTask
import org.jlleitschuh.gradle.ktlint.testdsl.GradleTestVersions
import org.junit.jupiter.api.io.TempDir
import java.io.File

@GradleTestVersions
abstract class AbstractPluginTest {

    @TempDir
    lateinit var temporaryFolder: File

    val projectRoot: File
        get() = temporaryFolder.resolve("plugin-test").apply { mkdirs() }

    val mainSourceSetCheckTaskName = GenerateReportsTask.generateNameForSourceSets(
        "main",
        GenerateReportsTask.LintType.CHECK
    )

    val mainSourceSetFormatTaskName = GenerateReportsTask.generateNameForSourceSets(
        "main",
        GenerateReportsTask.LintType.FORMAT
    )

    val kotlinScriptCheckTaskName = GenerateReportsTask.generateNameForKotlinScripts(
        GenerateReportsTask.LintType.CHECK
    )

    protected
    fun File.withCleanSources() = createSourceFile(
        "src/main/kotlin/$CLEAN_SOURCE_FILE",
        """
            val foo = "bar"

        """.trimIndent()
    )

    protected fun File.withCleanKotlinScript() = createSourceFile(
        KOTLIN_SCRIPT_FILE,
        """
            println("zzz")

        """.trimIndent()
    )

    protected fun File.withFailingKotlinScript() = createSourceFile(
        KOTLIN_SCRIPT_FAIL_FILE,
        """
            println("zzz")

        """.trimIndent()
    )

    protected
    fun File.withAlternativeFailingSources(baseDir: String) =
        createSourceFile("$baseDir/$FAIL_SOURCE_FILE", """val  foo    =     "bar"""")

    protected
    fun File.createSourceFile(sourceFilePath: String, contents: String) {
        val sourceFile = resolve(sourceFilePath)
        sourceFile.parentFile.mkdirs()
        sourceFile.writeText(contents)
    }
}
