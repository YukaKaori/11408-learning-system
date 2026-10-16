package com.yuka.learning.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Where packs come from and which version of each is used. */
class PackSourceTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void theHighestVersionOfEachPackWins(@TempDir Path dir) throws Exception {
        write(dir.resolve("a-v2.json"), pack("alpha", 2, "q-new"));
        write(dir.resolve("nested/b.json"), pack("beta", 1, "q-b"));
        List<PackSource.Loaded> loaded = PackSource.loadDirectory(mapper, dir);
        List<PackSource.Loaded> withOlder = new java.util.ArrayList<>(loaded);
        withOlder.add(new PackSource.Loaded(mapper.readValue(pack("alpha", 1, "q-old"), ContentPack.class),
                PackSource.Origin.CLASSPATH, "jar", "hash-old"));

        List<PackSource.Loaded> selected = PackSource.select(withOlder);

        assertThat(selected).extracting(l -> l.pack().pack()).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(selected).filteredOn(l -> l.pack().pack().equals("alpha")).singleElement()
                .satisfies(l -> {
                    assertThat(l.pack().version()).isEqualTo(2);
                    assertThat(l.origin()).isEqualTo(PackSource.Origin.DIRECTORY);
                });
    }

    @Test
    void theSameVersionWithDifferentContentIsAnError(@TempDir Path dir) throws Exception {
        write(dir.resolve("one.json"), pack("alpha", 3, "q-1"));
        write(dir.resolve("two.json"), pack("alpha", 3, "q-2"));
        List<PackSource.Loaded> loaded = PackSource.loadDirectory(mapper, dir);
        assertThatThrownBy(() -> PackSource.select(loaded))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("version 3 exists twice");
    }

    @Test
    void anUnknownFieldIsATypoNotASilentDrop(@TempDir Path dir) throws Exception {
        write(dir.resolve("typo.json"), """
                {"pack": "alpha", "title": "t", "version": 1,
                 "questions": [{"key": "q-1", "anwser": "B"}]}""");
        assertThatThrownBy(() -> PackSource.loadDirectory(mapper, dir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("anwser");
    }

    @Test
    void formattingDoesNotChangeTheHash(@TempDir Path dir) throws Exception {
        write(dir.resolve("compact.json"), pack("alpha", 1, "q-1"));
        String spaced = pack("alpha", 1, "q-1").replace(",", ",\n    ");
        Path other = Files.createDirectory(dir.resolve("other"));
        write(other.resolve("spaced.json"), spaced);
        assertThat(PackSource.loadDirectory(mapper, other).getFirst().hash())
                .isEqualTo(PackSource.loadDirectory(mapper, dir).stream()
                        .filter(l -> l.location().endsWith("compact.json")).findFirst().orElseThrow().hash());
    }

    private static String pack(String id, int version, String key) {
        return """
                {"pack": "%s", "title": "Pack %s", "version": %d,
                 "questions": [{"key": "%s", "subject": "cs408", "section": "cs408.choice", "type": "single_choice",
                   "points": ["cs408.ds.tree.binary"], "stem": "s", "options": ["a", "b"], "answer": "A"}]}"""
                .formatted(id, id, version, key);
    }

    private static void write(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
