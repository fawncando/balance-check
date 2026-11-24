-- Existing databases: allow clearing the stored video path after successful recognition.
USE balance_check;

DROP PROCEDURE IF EXISTS make_video_relative_path_nullable;
DELIMITER //
CREATE PROCEDURE make_video_relative_path_nullable()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'video'
          AND column_name = 'relative_path'
          AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE video MODIFY COLUMN relative_path VARCHAR(500) NULL;
    END IF;
END//
DELIMITER ;

CALL make_video_relative_path_nullable();
DROP PROCEDURE IF EXISTS make_video_relative_path_nullable;

DROP PROCEDURE IF EXISTS add_video_source_deleted_at;
DELIMITER //
CREATE PROCEDURE add_video_source_deleted_at()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'video'
          AND column_name = 'source_deleted_at'
    ) THEN
        ALTER TABLE video
            ADD COLUMN source_deleted_at DATETIME NULL
            COMMENT '源视频按策略清理的时间'
            AFTER relative_path;
    END IF;
END//
DELIMITER ;

CALL add_video_source_deleted_at();
DROP PROCEDURE IF EXISTS add_video_source_deleted_at;
