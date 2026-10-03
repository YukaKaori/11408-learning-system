package com.yuka.learning.exam.dto;

import com.yuka.learning.exam.syllabus.Syllabus;
import com.yuka.learning.exam.syllabus.SyllabusContent;

import java.util.List;

/**
 * The full syllabus — every paper, section, module, chapter and 考点 — in the
 * authored shape (see {@link SyllabusContent}). Static per deployment; clients
 * load it once and resolve every {@code nodeCode} they are sent against it.
 */
public record SyllabusResponse(String exam, String title, int syllabusYear,
                               List<SyllabusContent.Subject> subjects) {

    public static SyllabusResponse from(Syllabus syllabus) {
        SyllabusContent.Blueprint blueprint = syllabus.blueprint();
        return new SyllabusResponse(blueprint.exam(), blueprint.title(), blueprint.syllabusYear(),
                syllabus.subjectDocuments());
    }
}
