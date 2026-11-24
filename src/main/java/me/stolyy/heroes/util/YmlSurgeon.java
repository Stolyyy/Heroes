package me.stolyy.heroes.util;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comment-preserving yml edits, done as raw text surgery instead of a YamlConfiguration
 * load/set/save round-trip.
 *
 * <p>Why this exists: Bukkit's comment API only preserves comments attached to configuration
 * PATHS. Most comments in this project's ymls live where paths don't reach — inline after values
 * inside list items ({@code abilities.passives}), and block comments between list entries — so
 * {@code config.save()} structurally cannot keep them; every /balance edit was wiping them.
 * Here the file is edited as text: walk the indentation to the one line that owns the value,
 * replace the value token, touch nothing else.
 *
 * <p>Path semantics mirror {@link me.stolyy.heroes.hero.HeroConfig}'s list-aware resolution:
 * a segment is a mapping key, except when the current node is a list — then the segment matches
 * the list item whose {@code type:} equals it (e.g. {@code abilities.passives.bulkslam.damage}).
 *
 * <p>Every locate failure throws {@link IllegalStateException} naming what could not be found —
 * the file is never written on failure, and the edited text must re-parse as YAML before the
 * write happens (a value that breaks the parse would otherwise nuke the config on next reload,
 * because {@code YamlConfiguration.loadConfiguration} silently returns an empty config).
 */
public final class YmlSurgeon {

    private YmlSurgeon() {}

    /** Replaces the scalar value at {@code segments}, keeping indentation and comments intact. */
    public static void setScalar(File file, String[] segments, String value) {
        List<String> lines = read(file);
        int lineIdx = locate(file, lines, segments);

        String key = segments[segments.length - 1];
        // group1 = "<indent>[- ]key: ", group2 = value token, group3 = trailing inline comment
        Pattern valuePattern = Pattern.compile(
                "^(\\s*(?:- )?" + Pattern.quote(key) + ":\\s*)([^#]*?)(\\s*#.*)?$");
        Matcher m = valuePattern.matcher(lines.get(lineIdx));
        if (!m.matches()) {
            throw new IllegalStateException("Line for '" + key + "' in " + file.getName()
                    + " did not match a scalar entry: " + lines.get(lineIdx).trim());
        }
        if (m.group(2).isEmpty()) {
            throw new IllegalStateException("'" + String.join(".", segments) + "' in "
                    + file.getName() + " is a section, not a scalar value");
        }
        lines.set(lineIdx, m.group(1) + value + (m.group(3) != null ? m.group(3) : ""));
        write(file, lines);
    }

    /** Appends {@code entry} to the scalar list at {@code segments} (e.g. supported-modes). */
    public static void addListEntry(File file, String[] segments, String entry) {
        List<String> lines = read(file);
        int keyIdx = locate(file, lines, segments);
        int keyIndent = indentOf(lines.get(keyIdx));

        // Existing items decide the dash indent; an empty list defaults to the key's own indent
        // (the style used by the map ymls and hero passives alike).
        int itemIndent = keyIndent;
        int insertAt = keyIdx + 1;
        for (int i = keyIdx + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isBlankOrComment(line)) continue;
            int indent = indentOf(line);
            if (stripped(line).startsWith("- ") && indent >= keyIndent
                    && (insertAt == keyIdx + 1 || indent == itemIndent)) {
                if (scalarOf(line).equalsIgnoreCase(entry)) {
                    throw new IllegalStateException("'" + entry + "' is already in "
                            + String.join(".", segments) + " of " + file.getName());
                }
                itemIndent = indent;
                insertAt = i + 1;
                continue;
            }
            break;
        }
        lines.add(insertAt, " ".repeat(itemIndent) + "- " + entry);
        write(file, lines);
    }

    /** Removes {@code entry} from the scalar list at {@code segments}. */
    public static void removeListEntry(File file, String[] segments, String entry) {
        List<String> lines = read(file);
        int keyIdx = locate(file, lines, segments);
        int keyIndent = indentOf(lines.get(keyIdx));

        for (int i = keyIdx + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isBlankOrComment(line)) continue;
            int indent = indentOf(line);
            if (indent < keyIndent || (indent == keyIndent && !stripped(line).startsWith("- "))) break;
            if (stripped(line).startsWith("- ") && scalarOf(line).equalsIgnoreCase(entry)) {
                lines.remove(i);
                write(file, lines);
                return;
            }
        }
        throw new IllegalStateException("'" + entry + "' not found in "
                + String.join(".", segments) + " of " + file.getName());
    }

    // -----------------------------------------------------------------------
    // Path walking
    // -----------------------------------------------------------------------

    // Walks segments through the lines and returns the line index of the FINAL segment's key
    // line. Windows narrow as the walk descends: a mapping segment narrows to the key's block,
    // a list-type segment narrows to the matched "- type:" item's block.
    private static int locate(File file, List<String> lines, String[] segments) {
        int start = 0;
        int end = lines.size();
        int minIndent = 0;

        for (int si = 0; si < segments.length; si++) {
            String segment = segments[si];
            int found = findMappingKey(lines, start, end, minIndent, segment);
            if (found < 0) {
                found = findListItemByType(lines, start, end, minIndent, segment);
                if (found < 0) {
                    throw new IllegalStateException("Could not locate '" + segment + "' (in path "
                            + String.join(".", segments) + ") in " + file.getName());
                }
                if (si == segments.length - 1) return found;
                // Narrow to the item block: everything more indented than the dash line, until
                // the next item at the same indent or a dedent.
                int itemIndent = indentOf(lines.get(found));
                start = found; // the dash line itself may carry the next key ("- type: x")
                end = itemBlockEnd(lines, found, itemIndent);
                minIndent = itemIndent;
                continue;
            }

            if (si == segments.length - 1) return found;
            int keyIndent = indentOf(lines.get(found));
            start = found + 1;
            end = blockEnd(lines, found, keyIndent);
            // Children are more indented — except list items, which this style puts at the SAME
            // indent as their key ("passives:" / "- type:" both at indent 2).
            minIndent = keyIndent;
        }
        return -1; // unreachable: the final segment either returned or threw
    }

    private static int findMappingKey(List<String> lines, int start, int end, int minIndent, String key) {
        Pattern p = Pattern.compile("^(\\s*)(- )?" + Pattern.quote(key) + ":(\\s.*|)$");
        for (int i = start; i < end; i++) {
            String line = lines.get(i);
            if (isBlankOrComment(line)) continue;
            if (indentOf(line) < minIndent) break; // dedented out of the parent block
            if (p.matcher(line).matches()) return i;
        }
        return -1;
    }

    private static int findListItemByType(List<String> lines, int start, int end, int minIndent, String typeName) {
        Pattern p = Pattern.compile("^(\\s*)- type:\\s*" + Pattern.quote(typeName) + "\\s*(#.*)?$",
                Pattern.CASE_INSENSITIVE);
        for (int i = start; i < end; i++) {
            String line = lines.get(i);
            if (isBlankOrComment(line)) continue;
            if (indentOf(line) < minIndent) break;
            if (p.matcher(line).matches()) return i;
        }
        return -1;
    }

    // End of a mapping key's block: first line dedented below, or a same-indent non-item line.
    private static int blockEnd(List<String> lines, int keyIdx, int keyIndent) {
        for (int i = keyIdx + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isBlankOrComment(line)) continue;
            int indent = indentOf(line);
            if (indent < keyIndent) return i;
            if (indent == keyIndent && !stripped(line).startsWith("- ")) return i;
        }
        return lines.size();
    }

    // End of a list item's block: next item dash at the same indent, or any dedent below it.
    private static int itemBlockEnd(List<String> lines, int dashIdx, int itemIndent) {
        for (int i = dashIdx + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isBlankOrComment(line)) continue;
            int indent = indentOf(line);
            if (indent < itemIndent) return i;
            if (indent == itemIndent && stripped(line).startsWith("- ")) return i;
        }
        return lines.size();
    }

    // -----------------------------------------------------------------------
    // Line helpers + IO
    // -----------------------------------------------------------------------

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return i;
    }

    private static String stripped(String line) {
        return line.strip();
    }

    private static boolean isBlankOrComment(String line) {
        String s = line.strip();
        return s.isEmpty() || s.startsWith("#");
    }

    // The scalar of a "- value  # comment" list line.
    private static String scalarOf(String itemLine) {
        String s = stripped(itemLine).substring(2); // past "- "
        int hash = s.indexOf('#');
        if (hash >= 0) s = s.substring(0, hash);
        return s.strip();
    }

    private static List<String> read(File file) {
        try {
            return new ArrayList<>(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file.getName(), e);
        }
    }

    // Validates the result still parses as YAML, then writes. Never writes a broken file.
    private static void write(File file, List<String> lines) {
        String text = String.join("\n", lines) + "\n";
        try {
            new YamlConfiguration().loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException("Edit would corrupt " + file.getName()
                    + " (" + e.getMessage() + ") — nothing was written");
        }
        try {
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + file.getName(), e);
        }
    }
}
