-- Existing databases: add the actual video recording start time.
USE balance_check;

DROP PROCEDURE IF EXISTS add_video_recorded_at;
DELIMITER //
CREATE PROCEDURE add_video_recorded_at()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'video'
          AND column_name = 'recorded_at'
    ) THEN
        ALTER TABLE video
            ADD COLUMN recorded_at DATETIME NULL
            COMMENT '视频实际拍摄开始时间'
            AFTER balance_id;
    END IF;
END//
DELIMITER ;

CALL add_video_recorded_at();
DROP PROCEDURE IF EXISTS add_video_recorded_at;
