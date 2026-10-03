/**
 * Study time — the calendar's time-anchored study sessions, and the focus
 * timer that records them as they happen. The calendar read model merges
 * {@code study_sessions} (this package) with due-dated {@code learning_tasks}
 * (task package); tasks are not duplicated here.
 *
 * <p>Sessions are the one record of time spent studying: the analytics domain
 * (study time, streaks), Today's goal and the plan's hours per paper all read
 * them. The focus timer ({@link com.yuka.learning.calendar.FocusService}) is a
 * second way to <em>write</em> a session, never a second kind of time.
 * Reserved error-code range: 160000–169999.
 */
package com.yuka.learning.calendar;
