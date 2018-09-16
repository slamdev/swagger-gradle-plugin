# swagger-gradle-plugin [![Build Status](https://travis-ci.org/slamdev/swagger-gradle-plugin.svg?branch=master)](https://travis-ci.org/slamdev/swagger-gradle-plugin)

Gradle plugin to generate **Java** server and client code based on [Swagger/OpenAPI](http://swagger.io/) yaml files.

## Motivation

In a microservice world you need to provide some communication layer between your services. If you are choosing REST API
the workflow is usually the following: in service **A**(producer) you create REST controllers and some DTOs to describe 
you API. In service **B**(consumer) you create the same DTOs and some code to call the **A**'s endpoints. If 
requirements are changed you need to make modifications in both services. If there are multiple consumers there is a big
chance that you forgot to modify one of them.

The AIM of this plugin is to solve this issue and to generate some boilerplate for you communication layer.

## Usage

First step is to add the plugin to your project as described [here](https://plugins.gradle.org/plugin/com.github.slamdev.swagger).

Next step is to write specification of you API in [Swagger/OpenAPI](http://swagger.io/) yaml format. [Swagger editor](http://editor.swagger.io/)
is really helpful for this task.

## Tips and tricks

### Splitting specification

You can split you specification yaml file into multiple files. Plugin will automatically merge them into a single file 
(duplicate paths\definitions will be merged).

You can use swaggers `tag` key in the specification to group you endpoints.
