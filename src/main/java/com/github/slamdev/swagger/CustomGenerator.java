package com.github.slamdev.swagger;

import io.swagger.codegen.CodegenConfig;
import io.swagger.codegen.DefaultGenerator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class CustomGenerator extends DefaultGenerator {

    @Override
    public String getFullTemplateFile(CodegenConfig config, String templateFile) {
        String library = config.getLibrary();
        if (library != null && !"".equals(library)) {
            String libTemplateFile = String.join(File.separator, config.templateDir(), "libraries", library, templateFile);
            if (new File(libTemplateFile).exists()) {
                return libTemplateFile;
            }
        }
        return super.getFullTemplateFile(config, templateFile);
    }

    @Override
    public List<File> generate() {
        if (swagger != null && swagger.getVendorExtensions() != null) {
            config.vendorExtensions().putAll(swagger.getVendorExtensions());
        }
        return super.generate();
    }

    @Override
    public File writeToFile(String filename, String contents) throws IOException {
        if (filename.endsWith("spring.factories")) {
            Path file = Paths.get(filename);
            if (Files.exists(file)) {
                String originalContent = new String(Files.readAllBytes(file), UTF_8);
                contents = mergeSpringFactoriesContent(originalContent, contents);
            }
        }
        return super.writeToFile(filename, contents);
    }

    private String mergeSpringFactoriesContent(String originalContent, String contents) {
        StringBuilder builder = new StringBuilder(originalContent);
        builder.append(",\\\n");
        new BufferedReader(new StringReader(contents))
                .lines().skip(1)
                .forEach(builder::append);
        return builder.toString();
    }
}
