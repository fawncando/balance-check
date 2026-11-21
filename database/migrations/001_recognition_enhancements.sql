-- Existing databases: add identity, confidence and anomaly fields to video.
USE balance_check;

DROP PROCEDURE IF EXISTS add_video_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_video_column_if_missing(
    IN column_name_value VARCHAR(64),
    IN column_definition_value VARCHAR(500)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'video'
          AND column_name = column_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE video ADD COLUMN ', column_name_value,
                          ' ', column_definition_value);
        PREPARE statement_value FROM @ddl;
        EXECUTE statement_value;
        DEALLOCATE PREPARE statement_value;
    END IF;
END//
DELIMITER ;

CALL add_video_column_if_missing('person_card', 'VARCHAR(20) NULL');
CALL add_video_column_if_missing('identity_verified', 'TINYINT(1) NULL');
CALL add_video_column_if_missing('analysis_profile', 'VARCHAR(20) NULL');
CALL add_video_column_if_missing('analysis_confidence', 'DECIMAL(5,2) NULL');
CALL add_video_column_if_missing('anomaly_frames', 'INT NOT NULL DEFAULT 0');

DROP PROCEDURE IF EXISTS add_video_column_if_missing;
