package org.jlleitschuh.gradle.ktlint.worker

import com.pinterest.ktlint.core.KtLintRuleEngine
import com.pinterest.ktlint.core.LintError
import com.pinterest.ktlint.core.RuleProvider
import com.pinterest.ktlint.core.RuleSetProviderV2
import com.pinterest.ktlint.core.api.EditorConfigDefaults
import com.pinterest.ktlint.core.api.EditorConfigOverride
import com.pinterest.ktlint.core.api.editorconfig.CODE_STYLE_PROPERTY
import com.pinterest.ktlint.core.api.editorconfig.CodeStyleValue
import com.pinterest.ktlint.core.api.editorconfig.DISABLED_RULES_PROPERTY
import com.pinterest.ktlint.core.api.editorconfig.EditorConfigProperty
import net.swiftzer.semver.SemVer
import org.apache.commons.io.input.MessageDigestCalculatingInputStream
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.jlleitschuh.gradle.ktlint.worker.KtLintWorkAction.FormatTaskSnapshot.Companion.contentHash
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.ServiceLoader

@Suppress("UnstableApiUsage")
abstract class KtLintWorkAction : WorkAction<KtLintWorkAction.KtLintWorkParameters> {

    private val logger = Logging.getLogger("ktlint-worker")

    override fun execute() {
        val ruleProviders = loadRuleProvidersAndFilterThem(
            parameters.enableExperimental.getOrElse(false),
            parameters.disabledRules.getOrElse(emptySet())
        )

        val additionalEditorConfig = parameters
            .additionalEditorconfigFile
            .orNull
            ?.asFile
            ?.toPath()
        val editorConfigOverride = generateEditorConfigOverride()
        val debug = parameters.debug.get()
        val formatSource = parameters.formatSource.getOrElse(false)

        val result = mutableListOf<LintErrorResult>()
        val formattedFiles = mutableMapOf<File, ByteArray>()

        val ruleEngine = KtLintRuleEngine(
            ruleProviders = ruleProviders,
            editorConfigDefaults = EditorConfigDefaults.load(additionalEditorConfig),
            editorConfigOverride = editorConfigOverride,
        )

        resetEditorconfigCache(ruleEngine)

        parameters.filesToLint.files.forEach {
            val errors = mutableListOf<Pair<LintError, Boolean>>()

            try {
                if (formatSource) {
                    val currentFileContent = it.readText()
                    val updatedFileContent = ruleEngine.format(it.toPath()) { lintError, isCorrected ->
                        errors.add(lintError to isCorrected)
                    }

                    if (updatedFileContent != currentFileContent) {
                        formattedFiles[it] = contentHash(it)
                        it.writeText(updatedFileContent)
                    }
                } else {
                    ruleEngine.lint(it.toPath()) { lintError ->
                        errors.add(lintError to false)
                    }
                }
            } catch (e: RuntimeException) {
                throw GradleException(
                    "KtLint failed to parse file: ${it.absolutePath}",
                    e
                )
            }

            result.add(
                LintErrorResult(
                    lintedFile = it,
                    lintErrors = errors
                )
            )
        }

        KtLintClassesSerializer
            .create(
                SemVer.parse(parameters.ktLintVersion.get())
            )
            .saveErrors(
                result,
                parameters.discoveredErrorsFile.asFile.get()
            )

        if (formattedFiles.isNotEmpty()) {
            val snapshotFile = parameters.formatSnapshot.get().asFile
                .also { if (!it.exists()) it.createNewFile() }
            val snapshot = FormatTaskSnapshot(formattedFiles)
            FormatTaskSnapshot.writeIntoFile(snapshotFile, snapshot)
        }
    }

    private fun generateEditorConfigOverride(): EditorConfigOverride {
        val editorConfigOverrides = mutableListOf<Pair<EditorConfigProperty<*>, *>>()

        if (parameters.android.get()) {
            editorConfigOverrides +=
                CODE_STYLE_PROPERTY to CodeStyleValue.android.name
        }

        val disabledRules = parameters.disabledRules.get()
        if (disabledRules.isNotEmpty()) {
            editorConfigOverrides +=
                DISABLED_RULES_PROPERTY to disabledRules.joinToString(separator = ",")
        }

        return if (editorConfigOverrides.isNotEmpty()) {
            EditorConfigOverride.from(*editorConfigOverrides.toTypedArray())
        } else {
            EditorConfigOverride.EMPTY_EDITOR_CONFIG_OVERRIDE
        }
    }

    private fun resetEditorconfigCache(ruleEngine: KtLintRuleEngine) {
        if (parameters.editorconfigFilesWereChanged.get()) {
            logger.info("Resetting KtLint caches")
            // Calling trimMemory() will also reset internal loaded `.editorconfig` cache
            ruleEngine.trimMemory()
        }
    }

    private fun loadRuleProvidersAndFilterThem(
        enableExperimental: Boolean,
        disabledRules: Set<String>
    ): Set<RuleProvider> = loadRuleSetProviderFromClasspath()
        .filterKeys { enableExperimental || it != "experimental" }
        .filterKeys { !(disabledRules.contains("standard") && it == "\u0000standard") }
        .toSortedMap()
        .flatMap { it.value.getRuleProviders() }
        .toSet()

    private fun loadRuleSetProviderFromClasspath(): Map<String, RuleSetProviderV2> = ServiceLoader
        .load(RuleSetProviderV2::class.java)
        .associateBy {
            val key = it.id
            // Adapted from KtLint CLI module
            if (key == "standard") "\u0000$key" else key
        }

    interface KtLintWorkParameters : WorkParameters {
        val filesToLint: ConfigurableFileCollection
        val android: Property<Boolean>
        val disabledRules: SetProperty<String>
        val enableExperimental: Property<Boolean>
        val debug: Property<Boolean>
        val additionalEditorconfigFile: RegularFileProperty
        val formatSource: Property<Boolean>
        val discoveredErrorsFile: RegularFileProperty
        val ktLintVersion: Property<String>
        val editorconfigFilesWereChanged: Property<Boolean>
        val formatSnapshot: RegularFileProperty
    }

    /**
     * Represents pre-formatted files snapshot (file + it contents hash).
     */
    internal class FormatTaskSnapshot(
        val formattedSources: Map<File, ByteArray>
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L

            fun readFromFile(snapshotFile: File) =
                ObjectInputStream(snapshotFile.inputStream().buffered())
                    .use {
                        it.readObject() as FormatTaskSnapshot
                    }

            fun writeIntoFile(
                snapshotFile: File,
                formatSnapshot: FormatTaskSnapshot
            ) = ObjectOutputStream(snapshotFile.outputStream().buffered())
                .use {
                    it.writeObject(formatSnapshot)
                }

            fun contentHash(file: File): ByteArray {
                return MessageDigestCalculatingInputStream(file.inputStream().buffered()).use {
                    it.readBytes()
                    it.messageDigest.digest()
                }
            }
        }
    }
}
