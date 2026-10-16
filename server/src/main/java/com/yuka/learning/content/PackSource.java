package com.yuka.learning.content;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Where content packs come from, and which version of each is used.
 *
 * <p>Two sources: the packs shipped in the jar ({@link #CLASSPATH_PATTERN})
 * and, when configured, a directory of packs on the server
 * ({@code app.content.packs-dir}) — the second is how content ships without a
 * code release. Per pack id the highest {@code version} wins; the same version
 * with different content in two places is an error, never a coin toss.
 *
 * <p>Framework-light on purpose: the offline check ({@link ContentCheck}) loads
 * packs exactly as the server does.
 */
public final class PackSource {

    public static final String CLASSPATH_PATTERN = "classpath*:exam/questions/**/*.json";

    private PackSource() {
    }

    /** Where a pack was read from. */
    public enum Origin {
        CLASSPATH, DIRECTORY;

        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * A pack as read.
     *
     * @param location the file it came from, for messages
     * @param hash     SHA-256 of the pack's canonical serialization — formatting
     *                 changes do not change it, content changes do
     */
    public record Loaded(ContentPack pack, Origin origin, String location, String hash) {
    }

    /** Every pack on the classpath and in {@code directory} (when not blank), unselected. */
    public static List<Loaded> loadAll(ObjectMapper objectMapper, String directory) {
        List<Loaded> loaded = new ArrayList<>(loadClasspath(objectMapper));
        if (directory != null && !directory.isBlank()) {
            loaded.addAll(loadDirectory(objectMapper, Path.of(directory.trim())));
        }
        return loaded;
    }

    public static List<Loaded> loadClasspath(ObjectMapper objectMapper) {
        ObjectReader reader = strictReader(objectMapper);
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(CLASSPATH_PATTERN);
            List<Resource> sorted = new ArrayList<>(List.of(resources));
            sorted.sort(Comparator.comparing(PackSource::describe));
            List<Loaded> packs = new ArrayList<>(sorted.size());
            for (Resource resource : sorted) {
                try (InputStream in = resource.getInputStream()) {
                    packs.add(read(objectMapper, reader, in, Origin.CLASSPATH, describe(resource)));
                }
            }
            return packs;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read content packs from the classpath", e);
        }
    }

    public static List<Loaded> loadDirectory(ObjectMapper objectMapper, Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("Content packs directory does not exist: " + directory);
        }
        ObjectReader reader = strictReader(objectMapper);
        try (Stream<Path> files = Files.walk(directory)) {
            List<Path> paths = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
            List<Loaded> packs = new ArrayList<>(paths.size());
            for (Path path : paths) {
                try (InputStream in = Files.newInputStream(path)) {
                    packs.add(read(objectMapper, reader, in, Origin.DIRECTORY, path.toString()));
                }
            }
            return packs;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read content packs from " + directory, e);
        }
    }

    /**
     * One pack per id: the highest version. The same version twice is fine when
     * it is the same content (the directory copy wins), an error otherwise.
     */
    public static List<Loaded> select(List<Loaded> loaded) {
        Map<String, Loaded> chosen = new LinkedHashMap<>();
        for (Loaded candidate : loaded) {
            String id = candidate.pack().pack();
            if (id == null) {
                throw new IllegalStateException("Content pack " + candidate.location() + " declares no pack id");
            }
            Loaded current = chosen.get(id);
            if (current == null) {
                chosen.put(id, candidate);
                continue;
            }
            int was = versionOf(current);
            int is = versionOf(candidate);
            if (is > was || (is == was && candidate.hash().equals(current.hash())
                    && candidate.origin() == Origin.DIRECTORY)) {
                chosen.put(id, candidate);
            } else if (is == was && !candidate.hash().equals(current.hash())) {
                throw new IllegalStateException("Content pack '" + id + "' version " + is
                        + " exists twice with different content: " + current.location() + " and "
                        + candidate.location() + " — bump the version of the newer one");
            }
        }
        return List.copyOf(chosen.values());
    }

    /** SHA-256 of a value's canonical serialization (record component order). */
    public static String hash(ObjectMapper objectMapper, Object value) {
        try {
            byte[] canonical = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static int versionOf(Loaded loaded) {
        return loaded.pack().version() == null ? 0 : loaded.pack().version();
    }

    private static Loaded read(ObjectMapper objectMapper, ObjectReader reader, InputStream in, Origin origin,
                               String location) {
        ContentPack pack;
        try {
            pack = reader.readValue(in);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Content pack " + location + " is not a valid pack: " + e.getMessage(), e);
        }
        return new Loaded(pack, origin, location, hash(objectMapper, pack));
    }

    private static ObjectReader strictReader(ObjectMapper objectMapper) {
        return objectMapper.readerFor(ContentPack.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private static String describe(Resource resource) {
        try {
            return resource.getURL().toString();
        } catch (IOException e) {
            return String.valueOf(resource.getFilename());
        }
    }
}
