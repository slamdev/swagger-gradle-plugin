package com.github.slamdev.swagger

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.logging.progress.ProgressLogger
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.internal.progress.PercentageProgressFormatter

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Path

@CompileStatic
class SwaggerPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply(JavaPlugin)
        createGeneratedSourceSet(project)
        createGenerateTask(project)
    }

    @SuppressWarnings('GroovyAssignabilityCheck')
    private static void createGenerateTask(Project project) {
        Task task = project.tasks.create('swagger', SwaggerTask as Class<Task>) { SwaggerTask task ->
            task.destinationDir = project.file("${project.buildDir}/swagger-generated-sources")
        }
        project.tasks.withType(JavaCompile) { Task compileTask ->
            compileTask.dependsOn(task)
        }
    }

    private static void createGeneratedSourceSet(Project project) {
        SourceSetContainer sourceSets = project.convention.getPlugin(JavaPluginConvention).sourceSets
        SourceSet sourceSet = sourceSets.getByName('main')
        sourceSet.java.srcDir("${project.buildDir}/swagger-generated-sources/main/java")
        sourceSet.resources.srcDir("${project.buildDir}/swagger-generated-sources/main/resources")
    }

    @CacheableTask
    @SuppressWarnings('GroovyUnusedDeclaration')
    static class SwaggerTask extends DefaultTask {

        private final List<List<File>> clients = []

        private final List<List<File>> servers = []

        @PathSensitive(PathSensitivity.RELATIVE)
        @OutputDirectory
        File destinationDir

        private final ProgressLoggerFactory progressLoggerFactory

        @Inject
        SwaggerTask(ProgressLoggerFactory progressLoggerFactory) {
            this.progressLoggerFactory = progressLoggerFactory
        }

        void client(String fileName) {
            clients << [project.file(fileName)]
        }

        void client(File file) {
            clients << [file]
        }

        void client(List<File> files) {
            clients << files
        }

        void client(FileTree fileTree) {
            clients << (fileTree.files as List)
        }

        void client(FileCollection fileCollection) {
            servers << (fileCollection.files as List)
        }

        void server(String fileName) {
            servers << [project.file(fileName)]
        }

        void server(File file) {
            servers << [file]
        }

        void server(List<File> files) {
            servers << files
        }

        void server(FileTree fileTree) {
            servers << (fileTree.files as List)
        }

        void server(FileCollection fileCollection) {
            servers << (fileCollection.files as List)
        }

        @SkipWhenEmpty
        protected List getSpecs() {
            clients*.flatten() + servers*.flatten()
        }

        @InputFiles
        @PathSensitive(PathSensitivity.RELATIVE)
        protected List getClientSpecs() {
            clients*.flatten()
        }

        @InputFiles
        @PathSensitive(PathSensitivity.RELATIVE)
        protected List getServerSpecs() {
            clients*.flatten()
        }

        @SuppressWarnings('GroovyUnusedDeclaration')
        @TaskAction
        void generate() {
            ProgressLogger progressLogger = progressLoggerFactory.newOperation(SwaggerTask)
            progressLogger.start('Swagger code generation', null)
            try {
                PercentageProgressFormatter progressFormatter = new PercentageProgressFormatter('Generating',
                        specs.size() + 2)
                progressLogger.progress(progressFormatter.incrementAndGetProgress())
                destinationDir.deleteDir()
                progressLogger.progress(progressFormatter.incrementAndGetProgress())

                File tempDir = new File(destinationDir, 'temp')

                tempDir.mkdirs()
                servers.each { files ->
                    progressLogger.progress(progressFormatter.progress)
                    new SwaggerGenerator().generate(files, tempDir, 'server')
                    progressFormatter.increment()
                }
                clients.each { files ->
                    progressLogger.progress(progressFormatter.progress)
                    new SwaggerGenerator().generate(files, tempDir, 'client')
                    progressFormatter.increment()
                }
                FileTree javaTree = project
                        .fileTree(tempDir)
                        .include('**/*.java') as FileTree
                move(javaTree, 'java')
                FileTree resourcesTree = project
                        .fileTree(tempDir)
                        .exclude('**/*.java') as FileTree
                move(resourcesTree, 'resources')
                tempDir.deleteDir()
            } finally {
                progressLogger.completed()
            }
        }

        private void move(FileTree tree, String dir) {
            File tempDir = new File(destinationDir, 'temp')
            Path newDir = destinationDir.toPath().resolve('main').resolve(dir)
            for (File file : tree.files) {
                Path fileName = tempDir.toPath().relativize(file.toPath())
                Path newFile = newDir.resolve(fileName)
                if (!Files.exists(newFile.parent)) {
                    Files.createDirectories(newFile.parent)
                }
                Files.move(file.toPath(), newFile)
            }
        }
    }
}
