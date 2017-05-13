package com.github.slamdev.swagger;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collector;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.reducing;

public class YamlMerger {

    private static final Yaml YAML = new Yaml();

    public File merge(List<File> files, File outputDirectory) {
        if (files.isEmpty()) {
            throw new IllegalArgumentException("Files should not be empty");
        }
        if (files.size() == 1) {
            return files.get(0);
        }
        Collector<Map, ?, Map> reducer = reducing(new HashMap(), this::deepMerge);
        return files.stream().map(this::toString).map(YAML::load).map(Map.class::cast)
                .collect(collectingAndThen(collectingAndThen(reducer, YAML::dump), map -> toFile(map, outputDirectory)));
    }

    @SuppressWarnings("unchecked")
    private Map deepMerge(Map map1, Map map2) {
        Set<Entry> entries = map2.entrySet();
        for (Entry entry : entries) {
            Object value2 = entry.getValue();
            if (map1.containsKey(entry.getKey())) {
                Object value1 = map1.get(entry.getKey());
                if (value1 instanceof Map && value2 instanceof Map) {
                    deepMerge((Map) value1, (Map) value2);
                } else if (value1 instanceof List && value2 instanceof List) {
                    map1.put(entry.getKey(), merge((List) value1, (List) value2));
                } else {
                    map1.put(entry.getKey(), value2);
                }
            } else {
                map1.put(entry.getKey(), value2);
            }
        }
        return map1;
    }

    @SuppressWarnings("unchecked")
    private List merge(List list1, List list2) {
        list2.removeAll(list1);
        list1.addAll(list2);
        return list1;
    }

    private String toString(File file) {
        try {
            return new String(Files.readAllBytes(Paths.get(file.getPath())), UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
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
}
