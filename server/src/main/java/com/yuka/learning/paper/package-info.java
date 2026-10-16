/**
 * Papers — the exam as it is sat: papers composed from the bank, and the exam
 * stage's server.
 *
 * <p>A {@link com.yuka.learning.paper.entity.Paper} is a blueprint-structured
 * composition of questions ({@link com.yuka.learning.paper.PaperLayout}): a
 * library paper from a content pack, or one the candidate assembled
 * ({@link com.yuka.learning.paper.PaperAssembler}, 组卷, by the slots of the
 * syllabus content). A {@link com.yuka.learning.paper.entity.PaperSession} is a
 * paper being sat: the server keeps the clock, reveals nothing before the
 * hand-in, marks what the grader can judge, and lets the candidate score the
 * rest ({@link com.yuka.learning.paper.PaperSessionService}).
 *
 * <p>A paper sat here produces only ordinary evidence — attempts in the answer
 * log, the mistake book's entries, a {@code paper_sittings} record, a study
 * session — so mastery, analytics, the estimate and the plan move without
 * knowing it was sat on a screen.
 *
 * <p>Owns {@code papers} and {@code paper_sessions} (V11); library papers are
 * written by the {@code content} importer. Reserved error-code range:
 * 260000–269999.
 */
package com.yuka.learning.paper;
