package net.rs256.furnace.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ClasspathJarsTest {

    @Test
    void extractsVersionFromJarFileName() {
        assertEquals("1.11.2", ClasspathJars.versionFromFileName("vineflower", "vineflower-1.11.2.jar"));
        assertEquals("0.6.2", ClasspathJars.versionFromFileName("stitch", "stitch-0.6.2.jar"));
        assertEquals("unknown", ClasspathJars.versionFromFileName("stitch", "other-1.0.jar"));
    }

    @Test
    void findsToolVersionsOnTestClasspath() {
        // the test runtime classpath contains the real dependency jars
        assertEquals(false, ClasspathJars.findVersion("vineflower").equals("unknown"));
        assertEquals(false, ClasspathJars.findVersion("stitch").equals("unknown"));
    }
}
