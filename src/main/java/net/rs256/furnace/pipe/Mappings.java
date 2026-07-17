package net.rs256.furnace.pipe;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader;
import net.fabricmc.mappingio.format.tiny.Tiny2FileWriter;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

/**
 * Converts Mojang ProGuard mappings (named -> official) into tiny v2 files
 * with 'official' as the source namespace, ready for tiny-remapper
 * (SPEC 4.1 step 2, SPEC 4.2).
 */
public final class Mappings {

    public static final String NS_OFFICIAL = "official";
    public static final String NS_NAMED = "named";

    private Mappings() {}

    /** Parses a ProGuard mapping file as published by Mojang (named -> official). */
    public static MemoryMappingTree readProguard(Path proguardTxt) throws IOException {
        MemoryMappingTree tree = new MemoryMappingTree();
        try (Reader reader = Files.newBufferedReader(proguardTxt, StandardCharsets.UTF_8)) {
            ProGuardFileReader.read(reader, NS_NAMED, NS_OFFICIAL, tree);
        }
        return tree;
    }

    /** Writes the tree as tiny v2 with 'official' switched to the source namespace. */
    public static Path writeTiny(MemoryMappingTree namedToOfficial, Path outFile) throws IOException {
        MemoryMappingTree flipped = new MemoryMappingTree();
        namedToOfficial.accept(new MappingSourceNsSwitch(flipped, NS_OFFICIAL));
        try (Writer writer = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
            flipped.accept(new Tiny2FileWriter(writer, false));
        }
        return outFile;
    }

    /** Obfuscated (official) internal class names covered by the mapping. */
    public static Set<String> officialClassNames(MemoryMappingTree namedToOfficial) {
        int officialId = namedToOfficial.getNamespaceId(NS_OFFICIAL);
        Set<String> names = new HashSet<>();
        for (MappingTree.ClassMapping cls : namedToOfficial.getClasses()) {
            String name = cls.getName(officialId);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }
}
