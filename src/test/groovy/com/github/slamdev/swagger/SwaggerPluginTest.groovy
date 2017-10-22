package com.github.slamdev.swagger

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.hamcrest.Matcher
import org.junit.Before
import org.junit.Test

import static org.hamcrest.CoreMatchers.isA
import static org.junit.Assert.assertThat

@CompileStatic
class SwaggerPluginTest {

    Project project

    @Before
    void setUp() {
        project = ProjectBuilder.builder()
                .withName('test-project')
                .build()
        project.pluginManager.apply(JavaPlugin)
        project.pluginManager.apply(SwaggerPlugin)
    }

    @Test
    void plugin_should_add_generate_task_to_project() {
        Task task = project.tasks.getByName('swagger')
        assertThat(task, isA(SwaggerPlugin.SwaggerTask) as Matcher)
    }
}
