package com.github.slamdev.swagger;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import static java.util.Arrays.asList;

public class SwaggerGeneratorTest {

    private static final SwaggerGenerator GENERATOR = new SwaggerGenerator();

    @Test
    public void runGenerator() throws URISyntaxException, IOException {
        File spec1 = new File("src/test/resources", "spec1.yml");
        File spec2 = new File("src/test/resources", "spec2.yml");
        File spec3 = new File("src/test/resources", "spec3.yml");
        File outputDir = new File("build/output");
        FileUtils.deleteDirectory(outputDir);
        outputDir.mkdirs();
        GENERATOR.generate(asList(spec1, spec2, spec3), outputDir, "com.test", "super", "foo", false);
    }
}
