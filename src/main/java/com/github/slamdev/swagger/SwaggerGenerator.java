package com.github.slamdev.swagger;

import io.swagger.codegen.CodegenConstants;
import io.swagger.codegen.Generator;
import io.swagger.codegen.config.CodegenConfigurator;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

import static io.swagger.codegen.languages.AbstractJavaCodegen.DATE_LIBRARY;

public class SwaggerGenerator {

    public void generate(List<File> inputSpecs, File outputDir, String library) {
        File result = new YamlMerger().merge(inputSpecs, outputDir.getParentFile());
        Stream.of(result)
                .map(spec -> toConfiguration(spec, outputDir, library))
                .map(CodegenConfigurator::toClientOptInput)
                .map(opts -> new CustomGenerator().opts(opts))
                .forEach(Generator::generate);
    }

    private CodegenConfigurator toConfiguration(File inputSpec, File outputDir, String library) {
        CodegenConfigurator configurator = new CodegenConfigurator();
        configurator.setVerbose(false);
        configurator.setInputSpec(inputSpec.getPath());
        configurator.setOutputDir(outputDir.getPath());
        configurator.setLang(CustomJavaCodegen.class.getName());
//        configurator.setTemplateDir("src/main/resources/Java");
        configurator.setLibrary(library);
        configurator.addAdditionalProperty("java8", "true");
        configurator.addAdditionalProperty(DATE_LIBRARY, "java8");
        configurator.addAdditionalProperty(CodegenConstants.SOURCE_FOLDER, "");
        configurator.addAdditionalProperty(CodegenConstants.EXCLUDE_TESTS, true);
        configurator.addAdditionalProperty("jackson", "true");
        configurator.addAdditionalProperty("serializableModel", "true");
        return configurator;
    }
}
