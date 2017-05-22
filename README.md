# swagger-gradle-plugin [![Build Status](https://travis-ci.org/slamdev/swagger-gradle-plugin.svg?branch=master)](https://travis-ci.org/slamdev/swagger-gradle-plugin)

Gradle plugin to generate **Java** server and client code based on [Swagger/OpenAPI](http://swagger.io/) yaml files.

## Motivation

In a microservice world you need to provide some communication layer between your services. If you are choosing REST API
the workflow is usually the following: in service **A**(producer) you create REST controllers and some DTOs to describe 
you API. In service **B**(consumer) you create the same DTOs and some code to call the **A**'s endpoints. If 
requirements are changed you need to make modifications in both services. If there are multiple consumers there is a big
chance that you forgot to modify one of them.

The AIM of this plugin is to solve this issue and to generate some boilerplate for you communication layer.

Look into the [sample project repository](https://github.com/slamdev/swagger-gradle-plugin-sample) that shows how the 
generated code looks like and how to use it.

## Usage

First step is to add the plugin to your project as described [here](https://plugins.gradle.org/plugin/com.github.slamdev.swagger).

Next step is to write specification of you API in [Swagger/OpenAPI](http://swagger.io/) yaml format. [Swagger editor](http://editor.swagger.io/)
is really helpful for this task. This file needs to be placed to `src/main/resources/rest-api` directory of you project
and have `.yml` extension.

### Producer part

You can run `generateApi` gradle task and the server code for your API will be generated. It is added as to 
gradle main source set so it should be picked up by IDE automatically so you can use generated classes in you projects 
sources. Plugin generates Java DTOs for you swagger `definitions` (it uses [lombok](https://projectlombok.org/) library 
to add nice `builder` feature to DTOs) and Spring-based controller that describes your endpoint configuration like 
request types, paths, input\output parameters, etc. This controller is an **interface** so you need to implement it with 
the business logic of you service. [Producer code generated for the sample project looks like this](https://github.com/slamdev/swagger-gradle-plugin-sample/tree/master/producer/build/generated-sources/main).

If you modify the yaml file you need to re-run the `generateApi` gradle task to see the changes in generated classes.

### Consumer part

Consumer part is a bit tricky. Plugin generates a **jar** file that can be added as a library to your **consumer** 
microservice. If you have a `maven-publish` gradle plugin applied to your project then this library will be automatically
installed to the configured maven repo. [Consumer code generated for the sample project looks like this](https://github.com/slamdev/swagger-gradle-plugin-sample/tree/master/producer/build/client-api/sources).

For consumer code plugin generates the same Java DTOs plus Spring components to execute type-safe REST requests.
[Usage of consumer code looks like this](https://github.com/slamdev/swagger-gradle-plugin-sample/blob/master/consumer/src/main/java/com/github/slamdev/consumer/SampleService.java).

The following gradle tasks are used for consumer part:
* `generateClient` - generate code from swagger spec
* `processClientResources` - process resources (non-java files) from generated code
* `compileClient` - compile java files from the generated code
* `packageClient` - package compiled java files and processed resources to a jar file

If a `maven-publish` gradle plugin is applied, its `publish` task will install client artifact to a configured repository.

## Tips and tricks

### Splitting specification

You can split you specification yaml file into multiple files and store them in the `src/main/resources/rest-api` 
directory. Plugin will automatically merge them into a single file (duplicate paths\definitions will be merged).

You can use swaggers `tag` key in the specification to group you endpoints.

### Disabling consumer code generation

You can add the following configuration block to your `build.gradle` file to disabled consumer part generation 
completely:

```groovy
swagger {
    generateClient = false
}
```

### Set package names for generated code

By default package name is calculated from project `group` and project `name`. But you can specify it explicitly using 
this configuration block:

```groovy
swagger {
    apiPackageName = 'com.github.slamdev.sample.api'
    clientPackageName = 'com.github.slamdev.sample.api.client'
}
```
