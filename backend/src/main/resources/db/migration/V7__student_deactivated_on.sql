-- UC-STU-deactivate-student: record the date a student was deactivated (BR-deactivated-student-evaluation).
-- The column is mapped on Student only; instructors keep it NULL.
-- Invariant from here on: a student's enabled is false exactly when deactivated_on is set.
-- Students disabled before this change get today's date (the database server's), so the invariant
-- holds and they still appear in the peer evaluation report for every past week.

ALTER TABLE `peer_evaluation_user` ADD COLUMN `deactivated_on` date DEFAULT NULL;

UPDATE `peer_evaluation_user`
SET `deactivated_on` = CURRENT_DATE
WHERE `dtype` = 'Student' AND `enabled` = 0;
