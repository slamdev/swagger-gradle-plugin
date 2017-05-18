package com.github.slamdev.swagger

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.testfixtures.ProjectBuilder
import org.hamcrest.Matcher
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

import static org.hamcrest.CoreMatchers.is
import static org.hamcrest.CoreMatchers.isA
import static org.junit.Assert.assertThat

@Ignore
@CompileStatic
class SwaggerPluginTest {

    Project project

    @Before
    void setUp() {
        project = ProjectBuilder.builder()
                .withName('test-project')
                .build()
        project.pluginManager.apply(SwaggerPlugin)
    }

    @Test
    void plugin_should_add_generate_task_to_project() {
        Task task = project.tasks.getByName('generateApi')
        assertThat(task, isA(GenerateTask) as Matcher)
    }

    @CompileDynamic
    @Test
    void plugin_should_add_generated_sources_dir_to_project() {
        SourceSet main = project.convention.getPlugin(JavaPluginConvention).sourceSets
                .getByName(SwaggerPlugin.MAIN_SOURCE_SET)
        String path = main.output.dirs.files.first().path
        assertThat(path, is(SwaggerPlugin.API_OUTPUT_DIR(project)))
        String taskName = main.output.dirs.buildDependency.values.first()
        assertThat(taskName, is('generateApi'))
    }
}
