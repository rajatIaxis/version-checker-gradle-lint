package com.picpay.gradlelint.versioncheck

import com.android.tools.lint.detector.api.*
import java.io.File
import java.util.*


@Suppress("UnstableApiUsage")
class GradleVersionChecker : Detector(), Detector.GradleScanner {

    override fun checkDslPropertyAssignment(
        context: GradleContext,
        property: String,
        value: String,
        parent: String,
        parentParent: String?,
        valueCookie: Any,
        statementCookie: Any
    ) {
        if (parent == DEPENDENCIES && isCustomDependencyDeclaration(value)) {
            val library = getLibraryFromDependency(context, value)

            VersionRemoteDataSource(context.client)
                .getNewVersionAvailable(library)
                ?.let { newLibrary ->
                    context.report(
                        REMOTE_VERSION,
                        context.getLocation(valueCookie),
                        "New version available: $newLibrary\nActual: $library"
                    )
                }
        }
    }

    private fun getLibraryFromDependency(
        context: GradleContext,
        value: String
    ): Library {

        val buildSrc = findBuildSrc(context.project.dir)

        checkNotNull(buildSrc) { "buildSrc module not found." }

        val properties = readVersionLintProperties(context.project.dir)
        val dependenciesFileName = properties.getProperty(LINT_DEPENDENCIES_PROPERTY)
        val versionsFile = properties.getProperty(LINT_VERSIONS_PROPERTY)

        if (versionsFile != dependenciesFileName) {
            //TODO: fazer a busca em arquivos diferentes
        }

        val dependenciesFile = File(
            buildSrc.absolutePath,
            "src/main/java/$dependenciesFileName.kt"
        )

        val definition = mutableListOf<String>()

        for (line in dependenciesFile.readLines()) {
            val dependencyVarName = value.split(".")[1]
            if (line.tokenize().contains(dependencyVarName)) {
                val dependency = line.split("=")[1].trim()
                val dependencyCleaned = dependency.split("$")

                definition.add(dependencyCleaned[0].replace("\"", ""))

                val versionVarName = dependencyCleaned[1].getVarNameInVersionDeclaration()
                val versionNumber = if (versionVarName != dependencyVarName) {
                    getVersionValueFromFile(dependenciesFile, versionVarName)
                } else {
                    dependencyCleaned[0]
                }

                definition.add(versionNumber.replace("\"", ""))
                break
            }
        }
        return (definition[0] + definition[1]).toLibrary()
    }

    private fun readVersionLintProperties(projectDir: File): Properties {
        val versionLintProperties = File(findBuildSrc(projectDir), LINT_PROPERTIES)
        return Properties().apply {
            if (!versionLintProperties.exists()) {
                put(LINT_DEPENDENCIES_PROPERTY, "Dependencies")
                store(versionLintProperties.outputStream(), "Gradle Versions Lint")
            }
            load(versionLintProperties.inputStream())
        }
    }

    private fun isCustomDependencyDeclaration(value: String): Boolean {
        return value.startsWith("Dependencies.")
    }

    private fun findBuildSrc(currentProjectDir: File): File? {
        var dir: String? = currentProjectDir.parentFile?.absolutePath
        while (dir != null) {
            val currentDir = File(dir)

            val containsBuildSrc = currentDir.listFiles()
                ?.asList()
                ?.any { it.name == BUILD_SRC_MODULE }
                ?: false

            if (containsBuildSrc) {
                return File(currentDir.absolutePath, BUILD_SRC_MODULE)
            } else {
                dir = currentDir.parentFile?.absolutePath
            }
        }
        return null
    }

    private fun String.tokenize(delimiter: String = " "): List<String> {
        return this.split(delimiter).map { it.trim() }
    }

    private fun String.getVarNameInVersionDeclaration(): String {
        return replace("{", "")
            .replace("}", "")
            .replace("\"", "")
            .split(".")[1]
            .trim()
    }

    private fun getVersionValueFromFile(file: File, versionVar: String): String {
        file.readLines().forEach { line ->
            if (line.tokenize().contains(versionVar)) {
                return line.split("=")[1].trim()
            }
        }
        throw IllegalArgumentException(
            "Version with name $versionVar not found in file" +
                    " ${file.absolutePath}."
        )
    }

    companion object {

        private const val LINT_PROPERTIES = "versionlint.properties"
        private const val LINT_DEPENDENCIES_PROPERTY = "versionlint.dependencies.file"
        private const val LINT_VERSIONS_PROPERTY = "versionlint.versions.file"

        private const val BUILD_SRC_MODULE = "buildSrc"
        private const val DEPENDENCIES = "dependencies"

        private val IMPLEMENTATION = Implementation(
            GradleVersionChecker::class.java,
            Scope.GRADLE_SCOPE
        )

        @JvmField
        val REMOTE_VERSION = Issue.create(
            "PicPayVersionChecker",
            "Newer Library Versions Available",
            "This detector checks with a central repository to see if there are newer versions " +
                    "available for the dependencies used by this project. " +
                    "This is similar to the `GradleDependency` check, which checks for newer versions " +
                    "available in the Android SDK tools and libraries, but this works with any " +
                    "MavenCentral dependency, and connects to the library every time, which makes " +
                    "it more flexible but also *much* slower.",
            Category.MESSAGES,
            7,
            Severity.WARNING,
            IMPLEMENTATION
        ).setEnabledByDefault(true)

    }
}