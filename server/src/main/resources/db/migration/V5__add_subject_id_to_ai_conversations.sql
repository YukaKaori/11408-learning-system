-- ============================================================================
-- V5: Link AI conversations to real subjects.
--
-- Phase 7 introduces real Subject CRUD, closing the Phase 6 boundary where
-- conversations could only snapshot a client-supplied subject *name*.
-- subject_id becomes the logical FK used for server-side context resolution;
-- subject_name is kept as a denormalized display snapshot so conversation
-- lists stay readable even after the subject is renamed or deleted.
-- ============================================================================

ALTER TABLE ai_conversations
    ADD COLUMN subject_id BIGINT NULL COMMENT 'Logical FK → subjects.id; null = no subject context' AFTER title,
    MODIFY COLUMN subject_name VARCHAR(64) NULL COMMENT 'Denormalized subject name snapshot for display; survives subject rename/delete',
    ADD KEY idx_ai_conversations_subject_id (subject_id);
