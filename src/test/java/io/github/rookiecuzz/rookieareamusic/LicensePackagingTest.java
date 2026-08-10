package io.github.rookiecuzz.rookieareamusic;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicensePackagingTest {
    @Test
    void mitLicenseIsIncludedInMainOutput() throws IOException {
        Enumeration<URL> resources = getClass().getClassLoader()
                .getResources("META-INF/LICENSE");
        String license = null;
        while(resources.hasMoreElements()){
            URL resource = resources.nextElement();
            try(InputStream input = resource.openStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        input,
                        StandardCharsets.UTF_8
                ))){
                String candidate = reader.lines().collect(Collectors.joining("\n"));
                if(candidate.contains("Copyright (c) 2021 Niocho")){
                    license = candidate;
                    break;
                }
            }
        }

        assertNotNull(license, "Niocho's META-INF/LICENSE must be on the classpath");
        assertTrue(license.contains("MIT License"));
        assertTrue(license.contains("Permission is hereby granted, free of charge"));
        assertTrue(license.contains("THE SOFTWARE IS PROVIDED \"AS IS\""));
    }
}
