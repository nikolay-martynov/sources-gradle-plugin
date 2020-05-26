package com.github.sourcesgradleplugin

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.*
import org.gradle.api.component.Artifact
import org.gradle.api.component.Component
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.JvmLibrary
import org.gradle.language.base.artifact.SourcesArtifact

import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Task that resolves sources of all dependencies and puts them into specified directory.
 */
@CompileStatic
class GrabSourcesTask extends DefaultTask {

    /**
     * Configuration name from which dependencies are to be taken.
     */
    @Input
    String configurationName = 'runtimeClasspath'

    /**
     * Directory to which sources should be put.
     */
    @OutputDirectory
    File outputDirectory = project.file("${project.buildDir}/dependencies")

    /**
     * Type of the components to download.
     */
    @Input
    Class<? extends Component> componentType = JvmLibrary

    /**
     * Type of artifacts to download.
     */
    @Input
    Class<? extends Artifact> artifactType = SourcesArtifact

    /**
     * Fail build if some source could not be resolved.
     */
    @Input
    Boolean stopOnFailure = Boolean.FALSE

    /**
     * Patterns for artifacts that should be excluded.
     */
    @Input
    List<Pattern> exclude = []

    /**
     * Explicit artifacts to be used instead of downloading them.
     *
     * This is useful when repository does not contain source artifacts for some dependency.
     *
     * The key is {@link org.gradle.api.artifacts.component.ComponentSelector#getDisplayName()}
     * that is usually group:artifactId:version and the value is the file to be used.
     */
    @Input
    Map<String, File> explicit = [:]

    /**
     * Gets explicit files to be used instead of downloading them.
     *
     * This is for Gradle to determine if task is up to date.
     *
     * @return Explicit files.
     *
     * @see #explicit
     */
    @InputFiles
    Collection<File> getExplicitFiles() {
        explicit.values()
    }

    /**
     * Grabs sources of dependencies and puts them to output directory.
     */
    @TaskAction
    // Gradle returns results of multiple types and the only thing that distinguishes them is type so have to check it.
    @SuppressWarnings('Instanceof')
    // collectMany leads to mismatching generic types.
    @SuppressWarnings('UseCollectMany')
    void grabSources() {
        Set<? extends DependencyResult> dependencies = findDependenciesToResolve()
        List<UnresolvedDependencyResult> unresolvedDependencies = dependencies
                .findAll { it instanceof UnresolvedDependencyResult }*.asType(UnresolvedDependencyResult)
        if (unresolvedDependencies) {
            onResolutionFailure("Failed to resolve some dependencies: ${unresolvedDependencies*.requested}")
        }
        List<ResolvedDependencyResult> resolvedDependencies =
                dependencies.findAll { it instanceof ResolvedDependencyResult }*.asType(ResolvedDependencyResult)
        Set<ComponentIdentifier> resolvedIdentifiers = resolvedDependencies*.selected*.id.toSet()
        Set<ComponentResult> sourceResolutionResult = resolvedIdentifiers.collect {
            project.dependencies.createArtifactResolutionQuery().forComponents(it)
                    .withArtifacts(componentType, [artifactType]).execute().components
        }.flatten()*.asType(ComponentResult).toSet()
        List<UnresolvedComponentResult> unresolvedSources = sourceResolutionResult
                .findAll { it instanceof UnresolvedComponentResult }*.asType(UnresolvedComponentResult)
        if (unresolvedSources) {
            onResolutionFailure("Failed to resolve some sources: ${unresolvedSources*.id}")
        }
        List<ComponentArtifactsResult> resolvedSources =
                sourceResolutionResult.findAll { it instanceof ComponentArtifactsResult }
                        *.asType(ComponentArtifactsResult)
        Set<ArtifactResult> sourceArtifacts = resolvedSources.collect { componentArtifactsResult ->
            Set<ArtifactResult> artifacts = componentArtifactsResult.getArtifacts(artifactType)
            if (!artifacts) {
                onResolutionFailure("There are no artifacts of type ${artifactType.simpleName}" +
                        " for ${componentArtifactsResult.id}")
            }
            artifacts
        }.flatten()*.asType(ArtifactResult).toSet()
        List<UnresolvedArtifactResult> unresolvedArtifacts = sourceArtifacts
                .findAll { it instanceof UnresolvedArtifactResult }*.asType(UnresolvedArtifactResult)
        if (unresolvedArtifacts) {
            onResolutionFailure("Failed to resolve some sources: ${unresolvedArtifacts*.id}")
        }
        List<ResolvedArtifactResult> resolvedArtifacts = sourceArtifacts
                .findAll { it instanceof ResolvedArtifactResult }*.asType(ResolvedArtifactResult)
        Set<File> resolvedFiles = resolvedArtifacts*.file.toSet()
        resolvedFiles.addAll(explicitFiles)
        logger.info("Resolved sources: ${resolvedFiles}")
        project.copy { CopySpec spec ->
            spec.from(resolvedFiles)
            spec.into(outputDirectory)
        }
    }

    private void onResolutionFailure(String explanation) {
        if (stopOnFailure) {
            throw new GradleException(explanation)
        } else {
            logger.warn(explanation)
        }
    }

    private Set<? extends DependencyResult> findDependenciesToResolve() {
        Set<? extends DependencyResult> dependencies = project.configurations.getByName(configurationName)
                .incoming.resolutionResult.allDependencies.toSet()
        Set<? extends DependencyResult> notExcluded = dependencies.findAll { dependency ->
            Pattern matched = exclude.findResult { pattern ->
                Matcher matcher = pattern.matcher(dependency.requested.displayName)
                matcher.matches() ? pattern : null
            }
            if (matched != null) {
                logger.info("Excluding ${dependency.requested.displayName} because it matches ${matched.pattern()}")
            }
            matched == null
        }
        Set<? extends DependencyResult> notExplicit = notExcluded.findAll { dependency ->
            File explicitFile = explicit[dependency.requested.displayName]
            if (explicitFile) {
                logger.info("Excluding ${dependency.requested.displayName}" +
                        " because there is explicitly configured file for it ${explicitFile}")
            }
            !explicitFile
        }
        notExplicit
    }

}
