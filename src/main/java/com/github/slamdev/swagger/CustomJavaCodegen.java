package com.github.slamdev.swagger;

import io.swagger.codegen.*;
import io.swagger.codegen.languages.AbstractJavaCodegen;
import io.swagger.models.Model;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

public class CustomJavaCodegen extends AbstractJavaCodegen {

    public CustomJavaCodegen() {
        embeddedTemplateDir = "Java";
        supportedLibraries.put("client", "client");
        supportedLibraries.put("server", "server");
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.OTHER;
    }

    @Override
    public String getName() {
        return "java";
    }

    @Override
    public String getHelp() {
        return "Generates a Java library.";
    }

    @Override
    public void processOpts() {
        super.processOpts();
        String invokerFolder = (sourceFolder + '/' + invokerPackage).replace(".", "/");
        modelDocTemplateFiles.remove("model_doc.mustache");
        apiDocTemplateFiles.remove("api_doc.mustache");
        apiTestTemplateFiles.clear();
        typeMapping.put("DateTime", "Instant");
        typeMapping.put("File", "Resource");
        importMapping.put("Instant", "java.time.Instant");
        importMapping.put("Resource", "org.springframework.core.io.Resource");
        supportingFiles.add(new SupportingFile("lombok.config", "", "lombok.config"));
        if ("client".equals(library)) {
            supportingFiles.add(new SupportingFile("ApiConfiguration.mustache", invokerFolder, "ApiConfiguration.java"));
            supportingFiles.add(new SupportingFile("spring.factories.mustache", "META-INF", "spring.factories"));
        }
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        if (BooleanUtils.toBoolean(model.isEnum)) {
            if (additionalProperties.containsKey("jackson")) {
                model.imports.add("JsonCreator");
            }
        } else {
            if (additionalProperties.containsKey("jackson")) {
                model.imports.add("JsonProperty");
            }
        }
        model.imports.remove("ApiModelProperty");
        model.imports.remove("ApiModel");
    }

    @Override
    public CodegenModel fromModel(String name, Model model, Map<String, Model> allDefinitions) {
        CodegenModel codegenModel = super.fromModel(name, model, allDefinitions);
        codegenModel.imports.remove("ApiModel");
        return codegenModel;
    }

    public String toApiName(String name) {
        if ("Default".equals(name)) {
            name = "";
        }
        String prefix = (String) additionalProperties.get("apiNamePrefix");
        String result;
        if (name.isEmpty()) {
            result = prefix == null || prefix.isEmpty() ? "Default" : initialCaps(prefix);
        } else {
            result = prefix == null || prefix.isEmpty() ? initialCaps(name) : initialCaps(prefix) + initialCaps(name);
        }
        return result + "Api";
    }

    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> objs) {
        if ("server".equals(library)) {
            return postProcessServerOperations(objs);
        }
        return objs;
    }

    @Override
    public String toEnumName(CodegenProperty property) {
        return StringUtils.capitalize(property.name);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postProcessServerOperations(Map<String, Object> objs) {
        Map<String, List<CodegenOperation>> operations = (Map<String, List<CodegenOperation>>) objs.get("operations");
        if (operations != null) {
            List<CodegenOperation> ops = operations.get("operation");
            for (CodegenOperation operation : ops) {
                List<CodegenResponse> responses = operation.responses;
                if (responses != null) {
                    for (CodegenResponse end : responses) {
                        if ("0".equals(end.code)) {
                            end.code = "200";
                        }
                    }
                }
                if (operation.returnType == null) {
                    operation.hasReference = false;
                    operation.returnType = "Void";
                } else {
                    operation.hasReference = true;
                    String returnType;
                    int endIndex;
                    if (operation.returnType.startsWith("List")) {
                        returnType = operation.returnType;
                        endIndex = returnType.lastIndexOf('>');
                        if (endIndex > 0) {
                            operation.returnType = returnType.substring("List<".length(), endIndex).trim();
                            operation.returnContainer = "List";
                        }
                    } else if (operation.returnType.startsWith("Map")) {
                        returnType = operation.returnType;
                        endIndex = returnType.lastIndexOf('>');
                        if (endIndex > 0) {
                            operation.returnType = returnType.substring("Map<".length(), endIndex).split(",")[1].trim();
                            operation.returnContainer = "Map";
                        }
                    } else if (operation.returnType.startsWith("Set")) {
                        returnType = operation.returnType;
                        endIndex = returnType.lastIndexOf('>');
                        if (endIndex > 0) {
                            operation.returnType = returnType.substring("Set<".length(), endIndex).trim();
                            operation.returnContainer = "Set";
                        }
                    }
                }
            }
        }
        return objs;
    }
}
