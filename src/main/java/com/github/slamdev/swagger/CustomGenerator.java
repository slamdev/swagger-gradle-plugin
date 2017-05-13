package com.github.slamdev.swagger;

import io.swagger.codegen.CodegenConfig;
import io.swagger.codegen.DefaultGenerator;

import java.io.File;

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
}
