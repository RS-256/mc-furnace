package net.rs256.furnace.cfg;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * excludes.txt: gitignore-like glob patterns matched against forward-slash
 * paths relative to the tree root; the last matching pattern wins, lines
 * starting with '!' re-include.
 */
public final class ExcludeList {

    private record Rule(Pattern pattern, boolean negated) {}

    private final List<Rule> rules;

    private ExcludeList(List<Rule> rules) {
        this.rules = rules;
    }

    public static ExcludeList load(Path file) {
        if (!Files.exists(file)) {
            return new ExcludeList(List.of());
        }
        try {
            return fromLines(Files.readAllLines(file));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + file, e);
        }
    }

    public static ExcludeList fromLines(List<String> lines) {
        List<Rule> rules = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            boolean negated = line.startsWith("!");
            if (negated) {
                line = line.substring(1);
            }
            rules.add(new Rule(globToRegex(line), negated));
        }
        return new ExcludeList(rules);
    }

    /** Returns true if the given tree-relative path (forward slashes) is excluded. */
    public boolean isExcluded(String path) {
        boolean excluded = false;
        for (Rule rule : rules) {
            if (rule.pattern().matcher(path).matches()) {
                excluded = !rule.negated();
            }
        }
        return excluded;
    }

    static Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        // "**/" at a segment boundary also matches zero directories
                        if (i + 2 < glob.length() && glob.charAt(i + 2) == '/') {
                            regex.append("(?:.*/)?");
                            i += 3;
                        } else {
                            regex.append(".*");
                            i += 2;
                        }
                    } else {
                        regex.append("[^/]*");
                        i++;
                    }
                }
                case '?' -> {
                    regex.append("[^/]");
                    i++;
                }
                default -> {
                    if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                        regex.append('\\');
                    }
                    regex.append(c);
                    i++;
                }
            }
        }
        return Pattern.compile(regex.toString());
    }
}
