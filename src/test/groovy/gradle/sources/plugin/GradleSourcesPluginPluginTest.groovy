/*
 * This Groovy source file was generated by the Gradle 'init' task.
 */
package gradle.sources.plugin

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project
import spock.lang.Specification

/**
 * A simple unit test for the 'gradle.sources.plugin.greeting' plugin.
 */
public class GradleSourcesPluginPluginTest extends Specification {
    def "plugin registers task"() {
        given:
        def project = ProjectBuilder.builder().build()

        when:
        project.plugins.apply("gradle.sources.plugin.greeting")

        then:
        project.tasks.findByName("greeting") != null
    }
}