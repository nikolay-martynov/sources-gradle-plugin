package com.github.sourcesgradleplugin

import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plugin to grab sources of dependencies.
 *
 * Registers {@link GrabSourcesTask} task with name "grabSources".
 */
@CompileStatic
class SourcesGradlePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.tasks.register('grabSources', GrabSourcesTask)
    }

}
