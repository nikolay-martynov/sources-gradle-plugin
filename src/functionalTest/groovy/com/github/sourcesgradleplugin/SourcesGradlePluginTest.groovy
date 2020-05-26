package com.github.sourcesgradleplugin

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

import java.nio.file.Paths

class SourcesGradlePluginTest extends Specification {

    File testProjectDir

    File buildFile

    File outputDirectory

    def setup() {
        testProjectDir = File.createTempDir()
        testProjectDir.deleteOnExit()
        buildFile = new File(testProjectDir, 'build.gradle')
        outputDirectory = Paths.get(testProjectDir.path, 'build', 'dependencies').toFile()
    }

    def cleanup() {
        testProjectDir?.deleteDir()
    }

    def "can run default task with success"() {
        given:
        buildFile << """
plugins {
    id 'groovy'
    id 'com.github.sources-gradle-plugin'
}
"""
        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments("-s", "grabSources")
                .forwardOutput()
                .build()

        then:
        result.task(":grabSources").outcome == TaskOutcome.SUCCESS
    }

    def "downloads sources for specified configuration and ignores the other one"() {
        given:
        buildFile << """
plugins {
    id 'groovy'
    id 'com.github.sources-gradle-plugin'
}
repositories {
    mavenCentral()
}
dependencies {
    implementation 'org.codehaus.groovy:groovy-all:2.5.8'
    testImplementation 'org.spockframework:spock-core:1.3-groovy-2.5'
}
"""
        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments("-s", "grabSources")
                .forwardOutput()
                .build()

        then:
        result.task(":grabSources").outcome == TaskOutcome.SUCCESS
        outputDirectory.toPath().resolve('groovy-all-2.5.8-sources.jar').toFile().exists()
        outputDirectory.toPath().resolve('groovy-console-2.5.8-sources.jar').toFile().exists()
        !outputDirectory.toPath().resolve('spock-core-1.3-groovy-2.5-sources.jar').toFile().exists()
    }

    def "by default ignores missing source"() {
        given:
        buildFile << """
plugins {
    id 'groovy'
    id 'com.github.sources-gradle-plugin'
}
repositories {
    mavenCentral()
}
dependencies {
    implementation 'stax:stax-api:1.0'
}
"""
        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments("-s", "grabSources")
                .forwardOutput()
                .build()

        then:
        result.task(":grabSources").outcome == TaskOutcome.SUCCESS
    }

    def "can be configured to fail if cannot resolve some source"() {
        given:
        buildFile << """
plugins {
    id 'groovy'
    id 'com.github.sources-gradle-plugin'
}
repositories {
    mavenCentral()
}
dependencies {
    implementation 'stax:stax-api:1.0'
}
grabSources {
    stopOnFailure = true
}
"""
        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments("grabSources")
                .forwardOutput()
                .buildAndFail()

        then:
        result.task(":grabSources").outcome == TaskOutcome.FAILED
        result.output.contains("stax-api")
    }

    def "can be configured to ignore certain dependencies"() {
        given:
        buildFile << """
plugins {
    id 'groovy'
    id 'com.github.sources-gradle-plugin'
}
repositories {
    mavenCentral()
}
dependencies {
    implementation 'org.codehaus.groovy:groovy-all:2.5.8'
    implementation 'stax:stax-api:1.0'
}
grabSources {
    stopOnFailure = true
    exclude = [
        ~/.*:groovy-console:2.5.8/,
        ~/stax:.*/,
    ]
}
"""
        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments("-s", "grabSources")
                .forwardOutput()
                .build()

        then:
        result.task(":grabSources").outcome == TaskOutcome.SUCCESS
        outputDirectory.toPath().resolve('groovy-all-2.5.8-sources.jar').toFile().exists()
        !outputDirectory.toPath().resolve('groovy-console-2.5.8-sources.jar').toFile().exists()
    }

    def "can be configured to use explicit files for certain dependencies"() {
        given:
        File groovyConsoleSrc = new File(testProjectDir, "groovy-console-source.jar")
        groovyConsoleSrc.text = "java source"
        File staxApiSrc = new File(testProjectDir, "staxApi-source.jar")
        staxApiSrc.text = "java source"
        buildFile << """
plugins {
    id 'groovy'
    id 'com.github.sources-gradle-plugin'
}
repositories {
    mavenCentral()
}
dependencies {
    implementation 'org.codehaus.groovy:groovy-all:2.5.8'
    implementation 'stax:stax-api:1.0'
}
grabSources {
    stopOnFailure = true
    explicit = [
        'org.codehaus.groovy:groovy-console:2.5.8' : new File('$groovyConsoleSrc'),
        'stax:stax-api:1.0': new File('$staxApiSrc'),
    ]
}
"""
        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withPluginClasspath()
                .withArguments("-s", "grabSources")
                .forwardOutput()
                .build()

        then:
        result.task(":grabSources").outcome == TaskOutcome.SUCCESS
        outputDirectory.toPath().resolve('groovy-console-source.jar').toFile().exists()
        !outputDirectory.toPath().resolve('groovy-console-2.5.8-sources.jar').toFile().exists()
        outputDirectory.toPath().resolve('staxApi-source.jar').toFile().exists()
    }

}
