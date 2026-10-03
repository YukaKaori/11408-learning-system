package com.yuka.learning.exam.syllabus;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Loads the syllabus content from the classpath once, at startup, and
 * publishes it as the immutable {@link Syllabus} bean.
 *
 * <p>The syllabus is reference data that changes with the exam, not with user
 * activity, so it ships as reviewable content files beside the code — a diff
 * shows exactly which 考点 changed between syllabus years — rather than as rows
 * a migration inserts. Invalid content fails the boot (see {@link Syllabus#of}).
 */
@Configuration
public class SyllabusLoader {

    static final String BLUEPRINT = "exam/blueprint.json";
    static final String SUBJECT_TEMPLATE = "exam/syllabus/%s.json";

    @Bean
    public Syllabus syllabus(ObjectMapper objectMapper) {
        return load(objectMapper);
    }

    /** Framework-free entry point, shared with the unit tests. */
    public static Syllabus load(ObjectMapper objectMapper) {
        SyllabusContent.Blueprint blueprint = read(objectMapper, BLUEPRINT, SyllabusContent.Blueprint.class);
        List<SyllabusContent.Subject> subjects = blueprint.subjects().stream()
                .map(code -> read(objectMapper, SUBJECT_TEMPLATE.formatted(code), SyllabusContent.Subject.class))
                .toList();
        return Syllabus.of(blueprint, subjects);
    }

    private static <T> T read(ObjectMapper objectMapper, String path, Class<T> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read syllabus content " + path, e);
        }
    }
}
