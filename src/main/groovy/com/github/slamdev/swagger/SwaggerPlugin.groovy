package com.github.slamdev.swagger

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
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
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.internal.artifact.MavenArtifactNotationParserFactory
import org.gradle.api.publish.maven.internal.publication.DefaultMavenProjectIdentity
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.typeconversion.NotationParser

import javax.inject.Inject

@CompileStatic
class SwaggerPlugin implements Plugin<Project> {

    static final String GROUP = 'swagger'
    static final String API_DIRECTORY = 'rest-api'
    static final String MAIN_SOURCE_SET = 'main'
    static final Closure<String> API_OUTPUT_DIR = { Project p -> "${p.buildDir}/generated-sources/main" as String }
    static final Closure<String> CLIENT_OUTPUT_DIR = { Project p -> "${p.buildDir}/client-api/sources" as String }
    static final Closure<String> CLIENT_CLASSES_DIR = { Project p -> "${p.buildDir}/client-api/classes" as String }

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
        project.plugins.apply(JavaPlugin)
        project.plugins.apply(MavenPublishPlugin)
        createSwaggerConfiguration(project)
        createGenerateApiTask(project)
        createGenerateClientTask(project)
        createCompileClientTask(project)
        createPackageClientTask(project)
        createPublication(project)
        configureSourceSet(project)
    }

    static void configureSourceSet(Project project) {
        SourceSetOutput output = getMainSourceSet(project).output
        output.dir(['builtBy': 'generateApi'] as Map, API_OUTPUT_DIR(project))
    }

    static Configuration createSwaggerConfiguration(Project project) {
        project.repositories.mavenCentral()
        Configuration configuration = project.configurations.maybeCreate('swagger')
        configuration.dependencies.add(project.dependencies
                .create('org.springframework:spring-web:4.3.8.RELEASE'))
        configuration.dependencies.add(project.dependencies
                .create('org.springframework.boot:spring-boot-autoconfigure:1.5.3.RELEASE'))
        configuration.dependencies.add(project.dependencies
                .create('com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.8.8'))
        configuration.dependencies.add(project.dependencies
                .create('com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.8.8'))
        configuration.dependencies.add(project.dependencies
                .create('org.projectlombok:lombok:1.16.16'))
        configuration
    }

    Publication createPublication(Project project) {
        PublishingExtension publishing = project.extensions.getByType(PublishingExtension)
        Task artifactTask = project.getTasksByName('packageClient', false).first()
        Configuration configuration = project.configurations.getByName('swagger')
        MavenProjectIdentity projectIdentity = new DefaultMavenProjectIdentity(
                (project.group as String) ?: 'unspecified',
                "${project.name}-api-client",
                project.version as String
        )
        NotationParser<Object, MavenArtifact> artifactNotationParser = new MavenArtifactNotationParserFactory(
                instantiator, fileResolver).create()
        DefaultMavenPublication publication = new DefaultMavenPublication('apiClient', projectIdentity,
                artifactNotationParser, instantiator, projectDependencyResolver, fileCollectionFactory)
        publication.artifact(artifactTask)
        publication.pom(generatePom(configuration.dependencies))
        publishing.publications.add(publication)
        publication
    }

    @CompileDynamic
    static Action<MavenPom> generatePom(DependencySet dependencySet) {
        { MavenPom mavenPom ->
            mavenPom.withXml {
                asNode().children().last() + {
                    resolveStrategy = DELEGATE_FIRST
                    dependencies {
                        dependencySet.each {
                            Dependency d = it
                            dependency {
                                groupId d.group
                                artifactId d.name
                                version d.version
                            }
                        }
                    }
                }
            }
        } as Action
    }

    static JavaCompile createCompileClientTask(Project project) {
        JavaCompile task = project.task(
                [type: JavaCompile, dependsOn: 'generateClient'], 'compileClient'
        ) as JavaCompile
        task.source = project.fileTree(CLIENT_OUTPUT_DIR(project))
        task.include('**/*.*')
        task.classpath = project.configurations.getByName('swagger').asFileTree
        task.destinationDir = project.file(CLIENT_CLASSES_DIR(project))
        task.group = GROUP
        task
    }

    static Jar createPackageClientTask(Project project) {
        Jar task = project.task([type: Jar, dependsOn: 'compileClient'], 'packageClient') as Jar
        task.from(CLIENT_CLASSES_DIR(project))
        task.archiveName = 'api-client.jar'
        task.group = GROUP
        task
    }

    static GenerateTask createGenerateApiTask(Project project) {
        GenerateTask task = project.task([type: GenerateTask], 'generateApi') as GenerateTask
        task.source = getSpecsTree(project)
        task.destinationDir = project.file(API_OUTPUT_DIR(project))
        task.packageName = (project.group as String ?: 'undefined') + '.' + toPackageName(project.name) + '.api'
        task.apiNamePrefix = toClassName(project.name)
        task.client = false
        task.group = GROUP
        task
    }

    static String toPackageName(String name) {
        name.toLowerCase().replaceAll('-', '.')
    }

    static String toClassName(String name) {
        Collection<String> parts = name.toLowerCase().split('-').collect()
        parts.each { it.capitalize() }.join('')
    }

    static GenerateTask createGenerateClientTask(Project project) {
        GenerateTask task = project.task([type: GenerateTask], 'generateClient') as GenerateTask
        task.source = getSpecsTree(project)
        task.destinationDir = project.file(CLIENT_OUTPUT_DIR(project))
        task.packageName = (project.group as String ?: 'undefined') + '.' + toPackageName(project.name) + '.api.client'
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
}
