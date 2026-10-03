-- ============================================================================
-- V8: The 11408 foundation — the exam profile, and one anchor for everything.
--
-- The product stops being a generic learning workspace and becomes an 11408
-- postgraduate-entrance-exam system (政治 · 英语一 · 数学一 · 408). Two changes:
--
-- 1. exam_profiles — each candidate's exam: the target year (考研年份), the
--    first-round exam date once it is confirmed, and a target score per paper.
--
-- 2. Re-anchoring. Until now every artifact hung off a free-form, per-user
--    `subjects` row ("Machine Learning", "Japanese"…). In an 11408 system the
--    subjects are fixed and global, and they are only the ROOTS of a deeper
--    structure: the syllabus tree (subject → module → chapter → 考点), which
--    ships as versioned content (classpath:exam/syllabus/*.json), not as rows.
--    Every artifact now anchors to a node of that tree by its stable code
--    (e.g. `cs408.os.process.sync`) — one addressing scheme from a whole paper
--    down to a single 考点, shared by notes, decks, tasks, sessions, AI
--    conversations, materials and (V9) questions.
--
-- Expand/contract: this is the EXPAND half. `subject_id` columns and the
-- `subjects` table are retired — no code reads or writes them after V8 — but
-- they are kept, untouched, for one release so the change is reversible. A
-- later contract migration drops them. `learning_materials.subject_id` becomes
-- nullable because materials now anchor to a node instead.
--
-- Same conventions as V1–V7: snake_case, utf8mb4/utf8mb4_unicode_ci,
-- application-assigned snowflake ids, audit columns, logical foreign keys.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- exam_profiles — one row per candidate; absence means "defaults" (the next
-- exam, estimated date, no targets), exactly like user_preferences (V4).
--   target_year: 考研年份 — 2027 means the exam sat in December 2026.
--   exam_date:   the confirmed first-round date (day one: 政治 + 英语). NULL
--                means the application's estimate is used and shown as such.
-- ----------------------------------------------------------------------------
CREATE TABLE exam_profiles
(
    id              BIGINT   NOT NULL COMMENT 'Snowflake id assigned by the application',
    user_id         BIGINT   NOT NULL COMMENT 'Logical FK → users.id (owner, one row per user)',
    target_year     SMALLINT NOT NULL COMMENT '考研年份, e.g. 2027 = the exam sat in December 2026',
    exam_date       DATE     NULL COMMENT 'Confirmed first-round exam date (day one); NULL = estimated',
    target_politics SMALLINT NULL COMMENT 'Target score, 思想政治理论 (of 100)',
    target_english1 SMALLINT NULL COMMENT 'Target score, 英语（一） (of 100)',
    target_math1    SMALLINT NULL COMMENT 'Target score, 数学（一） (of 150)',
    target_cs408    SMALLINT NULL COMMENT 'Target score, 408 计算机学科专业基础 (of 150)',
    created_at      DATETIME NOT NULL COMMENT 'Audit: creation time',
    updated_at      DATETIME NOT NULL COMMENT 'Audit: last modification time',
    deleted         TINYINT  NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exam_profiles_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='Per-candidate exam target (defaults implied when absent)';

-- ----------------------------------------------------------------------------
-- The syllabus anchor. node_code is a code from the syllabus content, at any
-- depth: a subject root ("math1"), a module ("math1.linear"), a chapter or a
-- single 考点. Codes are hierarchical ("parent.child"), so a subtree is a
-- prefix match and is index-friendly. NULL = not anchored.
-- ----------------------------------------------------------------------------
ALTER TABLE notes
    ADD COLUMN node_code VARCHAR(64) NULL COMMENT 'Syllabus node this note is about; null = unanchored' AFTER subject_id,
    ADD KEY idx_notes_user_node (user_id, node_code);

ALTER TABLE flashcard_decks
    ADD COLUMN node_code VARCHAR(64) NULL COMMENT 'Syllabus node this deck covers; null = unanchored' AFTER subject_id,
    ADD KEY idx_flashcard_decks_user_node (user_id, node_code);

ALTER TABLE learning_tasks
    ADD COLUMN node_code VARCHAR(64) NULL COMMENT 'Syllabus node this task advances; null = unanchored' AFTER subject_id,
    ADD KEY idx_learning_tasks_user_node (user_id, node_code);

ALTER TABLE study_sessions
    ADD COLUMN node_code VARCHAR(64) NULL COMMENT 'Syllabus node studied in this session; null = unclassified' AFTER subject_id,
    ADD KEY idx_study_sessions_user_node (user_id, node_code);

ALTER TABLE ai_conversations
    ADD COLUMN node_code VARCHAR(64) NULL COMMENT 'Syllabus node the conversation is scoped to; null = general' AFTER subject_id,
    ADD KEY idx_ai_conversations_user_node (user_id, node_code);

ALTER TABLE learning_materials
    ADD COLUMN node_code VARCHAR(64) NULL COMMENT 'Syllabus node this material serves; null = general reference' AFTER subject_id,
    MODIFY COLUMN subject_id BIGINT NULL COMMENT 'RETIRED in V8 — superseded by node_code',
    ADD KEY idx_learning_materials_user_node (user_id, node_code);

-- ----------------------------------------------------------------------------
-- Best-effort carry-over from the retired free-form subjects. Only names whose
-- meaning is unambiguous map to a node; anything else stays unanchored rather
-- than guessed. Runs once, on whatever data exists when V8 is applied.
-- ----------------------------------------------------------------------------
CREATE TEMPORARY TABLE v8_subject_nodes AS
SELECT id AS subject_id,
       CASE
           WHEN name LIKE '%数据结构%' THEN 'cs408.ds'
           WHEN name LIKE '%组成原理%' OR name LIKE '%计组%' THEN 'cs408.co'
           WHEN name LIKE '%操作系统%' THEN 'cs408.os'
           WHEN name LIKE '%计算机网络%' OR name LIKE '%计网%' THEN 'cs408.cn'
           WHEN name LIKE '%408%' THEN 'cs408'
           WHEN name LIKE '%线性代数%' OR name LIKE '%线代%' THEN 'math1.linear'
           WHEN name LIKE '%概率%' THEN 'math1.probability'
           WHEN name LIKE '%高等数学%' OR name LIKE '%高数%' THEN 'math1.calculus'
           WHEN name LIKE '%数学%' THEN 'math1'
           WHEN name LIKE '%英语%' THEN 'english1'
           WHEN name LIKE '%政治%' THEN 'politics'
       END AS node_code
FROM subjects
WHERE deleted = 0;

UPDATE notes n JOIN v8_subject_nodes m ON n.subject_id = m.subject_id
SET n.node_code = m.node_code
WHERE m.node_code IS NOT NULL;

UPDATE flashcard_decks d JOIN v8_subject_nodes m ON d.subject_id = m.subject_id
SET d.node_code = m.node_code
WHERE m.node_code IS NOT NULL;

UPDATE learning_tasks t JOIN v8_subject_nodes m ON t.subject_id = m.subject_id
SET t.node_code = m.node_code
WHERE m.node_code IS NOT NULL;

UPDATE study_sessions s JOIN v8_subject_nodes m ON s.subject_id = m.subject_id
SET s.node_code = m.node_code
WHERE m.node_code IS NOT NULL;

UPDATE ai_conversations c JOIN v8_subject_nodes m ON c.subject_id = m.subject_id
SET c.node_code = m.node_code
WHERE m.node_code IS NOT NULL;

UPDATE learning_materials l JOIN v8_subject_nodes m ON l.subject_id = m.subject_id
SET l.node_code = m.node_code
WHERE m.node_code IS NOT NULL;

DROP TEMPORARY TABLE v8_subject_nodes;

ALTER TABLE subjects
    COMMENT = 'RETIRED in V8 — superseded by the syllabus tree; kept read-only for rollback, dropped by a later contract migration';
