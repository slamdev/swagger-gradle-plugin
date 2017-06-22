package com.github.slamdev.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

public class YamlMerger {

    public File merge(List<File> specs, File outputDirectory) {
        List<File> files = new ArrayList<>(specs);
        if (files.isEmpty()) {
            throw new IllegalArgumentException("Files should not be empty");
        }
        if (files.size() == 1) {
            return files.get(0);
        }
        YamlMapper mapper = new YamlMapper();
        JsonNode all = mapper.read(files.remove(0));
        for (File file : files) {
            all = merge(all, mapper.read(file));
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            mapper.write(new YAMLFactory().createGenerator(baos), all);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return toFile(baos.toString(), outputDirectory);
    }

    private JsonNode merge(JsonNode mainNode, JsonNode updateNode) {
        Iterator<String> fieldNames = updateNode.fieldNames();
        while (fieldNames.hasNext()) {
            String updatedFieldName = fieldNames.next();
            JsonNode valueToBeUpdated = mainNode.get(updatedFieldName);
            JsonNode updatedValue = updateNode.get(updatedFieldName);
            if (valueToBeUpdated != null && valueToBeUpdated.isArray() && updatedValue.isArray()) {
                ArrayNode updatedArrayNode = (ArrayNode) updatedValue;
                ArrayNode arrayNodeToBeUpdated = (ArrayNode) valueToBeUpdated;
                for (int i = 0; updatedArrayNode.has(i); ++i) {
                    if (arrayNodeToBeUpdated.has(i)) {
                        JsonNode mergedNode = merge(arrayNodeToBeUpdated.get(i), updatedArrayNode.get(i));
                        arrayNodeToBeUpdated.set(i, mergedNode);
                    } else {
                        arrayNodeToBeUpdated.add(updatedArrayNode.get(i));
                    }
                }
                // if the Node is an @ObjectNode
            } else if (valueToBeUpdated != null && updatedValue != null && valueToBeUpdated.isObject() && !updatedValue.isNull()) {
                merge(valueToBeUpdated, updatedValue);
            } else {
                if (updatedValue == null || updatedValue.isNull()) {
                    ((ObjectNode) mainNode).remove(updatedFieldName);
                } else {
                    ((ObjectNode) mainNode).replace(updatedFieldName, updatedValue);
                }
            }
        }
        if (updateNode instanceof TextNode) {
            return updateNode;
        }
        return mainNode;
    }


    private File toFile(String content, File outputDirectory) {
        Path file = Paths.get(outputDirectory.getPath(), "merged.yml");
        try {
            Files.write(file, content.getBytes(UTF_8), TRUNCATE_EXISTING, CREATE);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return file.toFile();
    }

    private static class YamlMapper {

        private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

        JsonNode read(File file) {
            try {
                return MAPPER.readTree(file);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }

        void write(YAMLGenerator generator, JsonNode merged) {
            try {
                MAPPER.writeTree(generator, merged);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }
}
