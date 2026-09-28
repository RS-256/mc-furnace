package net.rs256.furnace.pipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DecompileOptionsTest {

    @Test
    void keepsWhitespaceInQuotedValuesAndFileOrder(@TempDir Path tmp) throws Exception {
        Path file = write(tmp, """
                # comment
                dgs=1

                ind="    "
                """);

        List<String[]> options = Decompile.loadOptions(file);
        assertEquals(2, options.size());
        assertArrayEquals(new String[] {"dgs", "1"}, options.get(0));
        assertArrayEquals(new String[] {"ind", "    "}, options.get(1));
    }

    @Test
    void rejectsValuesThatTrimToNothing(@TempDir Path tmp) throws Exception {
        // An unquoted whitespace value would reach Vineflower as "-ind=", which
        // is short enough that it gets taken for an input path instead.
        Path file = write(tmp, "ind=    \n");
        assertThrows(IOException.class, () -> Decompile.loadOptions(file));
    }

    private static Path write(Path tmp, String content) throws IOException {
        Path file = tmp.resolve("decompiler.properties");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
