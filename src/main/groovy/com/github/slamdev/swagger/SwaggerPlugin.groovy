package com.github.slamdev.swagger

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.FileTree
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.Publication
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.internal.ProjectDependencyPublicationResolver
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.internal.artifact.MavenArtifactNotationParserFactory
import org.gradle.api.publish.maven.internal.dependencies.DefaultMavenDependency
import org.gradle.api.publish.maven.internal.publication.DefaultMavenProjectIdentity
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel

import javax.inject.Inject

@CompileStatic
class SwaggerPlugin implements Plugin<Project> {

    static final String GROUP = 'swagger'
    static final String API_DIRECTORY = 'rest-api'
    static final String MAIN_SOURCE_SET = 'main'
    static final Closure<String> API_OUTPUT_DIR = { Project p -> "${p.buildDir}/generated-sources/main" as String }
    static final Closure<String> CLIENT_OUTPUT_DIR = { Project p -> "${p.buildDir}/client-api/sources" as String }
    static final Closure<String> CLIENT_CLASSES_DIR = { Project p -> "${p.buildDir}/client-api/classes" as String }
    static final Closure<String> CLIENT_JAR_DIR = { Project p -> "${p.buildDir}/client-api/lib" as String }

    private final Instantiator instantiator
    private final DependencyMetaDataProvider dependencyMetaDataProvider
    private final FileResolver fileResolver
    private final ProjectDependencyPublicationResolver projectDependencyResolver
    private final FileCollectionFactory fileCollectionFactory

    @Inject
    SwaggerPlugin(Instantiator instantiator, DependencyMetaDataProvider dependencyMetaDataProvider,
                  FileResolver fileResolver, ProjectDependencyPublicationResolver projectDependencyResolver,
                  FileCollectionFactory fileCollectionFactory) {
        this.instantiator = instantiator
        this.dependencyMetaDataProvider = dependencyMetaDataProvider
        this.fileResolver = fileResolver
        this.projectDependencyResolver = projectDependencyResolver
        this.fileCollectionFactory = fileCollectionFactory
    }

    @Override
    void apply(Project project) {
        SwaggerExtension extension = project.extensions.create('swagger', SwaggerExtension)
        if (!extension.generateClient && !extension.generateApi) {
            return
        }
        project.plugins.apply(JavaPlugin)
        project.plugins.apply(IdeaPlugin)
        project.afterEvaluate {
            if (extension.generateApi) {
                createGenerateApiTask(project)
                configureSourceSet(project)
            }
            if (extension.generateClient) {
                createGenerateClientTask(project)
                createProcessClientResourcesTask(project)
                createCompileClientTask(project)
                createPackageClientTask(project)
                createClientApiConfiguration(project)
                if (project.plugins.hasPlugin(MavenPublishPlugin)) {
                    createPublication(project)
                }
            }
        }
    }

    static void configureSourceSet(Project project) {
        SourceSet sourceSet = getMainSourceSet(project)
        sourceSet.java.srcDir(API_OUTPUT_DIR(project))
        IdeaModel idea = project.extensions.getByType(IdeaModel)
        idea.module.generatedSourceDirs += project.file(API_OUTPUT_DIR(project))
    }

    static Configuration createSwaggerConfiguration(Project project) {
        project.repositories.mavenCentral()
        Configuration configuration = project.configurations.maybeCreate('swagger')
        List<String> dependencies = [
                'org.springframework:spring-web:4.3.10.RELEASE',
                'org.springframework.boot:spring-boot-autoconfigure:1.5.6.RELEASE',
                'com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.8.9',
                'com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.8.9',
                'org.projectlombok:lombok:1.16.18'
        ]
        dependencies.collect { project.dependencies.create(it) }.each { configuration.dependencies.add(it) }
        configuration
    }

    static Configuration createClientApiConfiguration(Project project) {
        Jar artifactTask = project.getTasksByName('packageClient', false).first() as Jar
        Configuration configuration = project.configurations.maybeCreate('clientApi')
        project.artifacts.add('clientApi', artifactTask)
        configuration.extendsFrom(project.configurations.getByName('swagger'))
        configuration
    }

    @CompileDynamic
    Publication createPublication(Project project) {
        PublishingExtension publishing = project.extensions.getByType(PublishingExtension)
        Jar artifactTask = project.getTasksByName('packageClient', false).first() as Jar
        Configuration configuration = project.configurations.getByName('swagger')
        MavenProjectIdentity projectIdentity = new DefaultMavenProjectIdentity(
                project.group as String ?: 'unspecified',
                artifactTask.baseName,
                artifactTask.version
        )
        NotationParser<Object, MavenArtifact> artifactNotationParser = new MavenArtifactNotationParserFactory(
                instantiator, fileResolver).create()
        DefaultMavenPublication publication = new DefaultMavenPublication('apiClient', projectIdentity,
                artifactNotationParser, instantiator, projectDependencyResolver, fileCollectionFactory)
        publication.artifact(artifactTask)
        configuration.dependencies.each { Dependency d ->
            publication.runtimeDependencies.add(new DefaultMavenDependency(d.group, d.name, null))
        }
        publishing.publications.add(publication)
        publication
    }

    static ProcessResources createProcessClientResourcesTask(Project project) {
        ProcessResources task = project.task(
                [type: ProcessResources, dependsOn: 'generateClient'], 'processClientResources'
        ) as ProcessResources
        task.from(project.fileTree(CLIENT_OUTPUT_DIR(project)))
        task.include('**/*.*')
        task.exclude('**/*.java')
        task.destinationDir = project.file(CLIENT_CLASSES_DIR(project))
        task.group = GROUP
        task
    }

    static JavaCompile createCompileClientTask(Project project) {
        createSwaggerConfiguration(project)
        JavaCompile task = project.task(
                [type: JavaCompile, dependsOn: 'processClientResources'], 'compileClient'
        ) as JavaCompile
        task.source = project.fileTree(CLIENT_OUTPUT_DIR(project))
        task.include('**/*.java')
        task.classpath = project.configurations.getByName('swagger').asFileTree
        task.destinationDir = project.file(CLIENT_CLASSES_DIR(project))
        task.group = GROUP
        task
    }

    @CompileDynamic
    static Jar createPackageClientTask(Project project) {
        if (project.plugins.hasPlugin('org.springframework.boot')) {
            project.getTasksByName('bootRepackage', false).each { it.withJarTask = 'jar' }
        }
        Jar task = project.task([type: Jar, dependsOn: 'compileClient'], 'packageClient') as Jar
        task.from(CLIENT_CLASSES_DIR(project))
        task.baseName = "${project.name}-api-client"
        task.version = project.version as String
        task.group = GROUP
        task.destinationDir = project.file(CLIENT_JAR_DIR(project))
        task
    }

    static GenerateTask createGenerateApiTask(Project project) {
        GenerateTask task = project.task([type: GenerateTask], 'generateApi') as GenerateTask
        task.source = getSpecsTree(project)
        task.destinationDir = project.file(API_OUTPUT_DIR(project))
        task.packageName = calculatePackageName(project, false)
        task.apiNamePrefix = toClassName(project.name)
        task.client = false
        task.group = GROUP
        project.getTasksByName('compileJava', false).each { it.dependsOn(task) }
        task
    }

    static String toPackageName(String name) {
        name.toLowerCase().split('/').last().replaceAll('-', '.')
    }

    static String toClassName(String name) {
        name.toLowerCase().split('/').last().split('-').collect { ((String) it).capitalize() }.join('')
    }

    static GenerateTask createGenerateClientTask(Project project) {
        GenerateTask task = project.task([type: GenerateTask], 'generateClient') as GenerateTask
        task.source = getSpecsTree(project)
        task.destinationDir = project.file(CLIENT_OUTPUT_DIR(project))
        task.packageName = calculatePackageName(project, true)
        task.apiNamePrefix = toClassName(project.name)
        task.pathVariableName = toPackageName(project.name) + '.url'
        task.client = true
        task.group = GROUP
        task
    }

    static SourceSet getMainSourceSet(Project p) {
        p.convention.getPlugin(JavaPluginConvention).sourceSets.getByName(MAIN_SOURCE_SET)
    }

    @CompileDynamic
    static FileTree getSpecsTree(Project p) {
        SourceDirectorySet resources = getMainSourceSet(p).resources
        resources.matching {
            include "${API_DIRECTORY}/**/*.yml"
        }
    }

    static String calculatePackageName(Project p, boolean client) {
        SwaggerExtension extension = p.extensions.getByType(SwaggerExtension)
        if (extension.apiPackageName != null && !client) {
            extension.apiPackageName
        } else if (extension.clientPackageName != null && client) {
            extension.clientPackageName
        } else {
            (p.group as String ?: 'undefined') + '.' + toPackageName(p.name) + '.api' + (client ? '.client' : '')
        }
    }
}
