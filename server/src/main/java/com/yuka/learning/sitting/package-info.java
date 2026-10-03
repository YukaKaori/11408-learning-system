/**
 * Sittings — papers sat under exam conditions (真题 by year, 模拟卷 by name) and
 * the scores they earned, section by section.
 *
 * <p>The only paper-level evidence in the system. The mastery model answers
 * "how much of this paper's score is secured, 考点 by 考点"; a sitting answers
 * "what does a whole paper, in 180 minutes, actually yield" — the number a
 * candidate's target is written in. {@link com.yuka.learning.sitting.ScoreEstimate}
 * turns recent sittings into a per-paper estimate and a per-section profile
 * (where the points are lost); the plan reads the estimate to decide where
 * time goes.
 *
 * <p>Owns {@code paper_sittings} (V10). Reserved error-code range:
 * 250000–259999.
 */
package com.yuka.learning.sitting;
