package com.github.slamdev.swagger

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction

class GenerateTask extends SourceTask {

    private static final SwaggerGenerator GENERATOR = new SwaggerGenerator()

    @OutputDirectory
    File destinationDir

    @Input
    String packageName = 'undefined'

    @Input
    boolean client

    @Input
    String apiNamePrefix

    @Input
    String pathVariableName = ''

    @SuppressWarnings('GroovyUnusedDeclaration')
    @TaskAction
    protected void generate() {
        GENERATOR.generate(source.files as List, destinationDir, packageName, apiNamePrefix, pathVariableName, client)
    }
}
