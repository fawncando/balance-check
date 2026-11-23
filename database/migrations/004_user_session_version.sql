-- Existing databases: invalidate active sessions when a password is reset.
USE balance_check;

DROP PROCEDURE IF EXISTS add_user_session_version;
DELIMITER //
CREATE PROCEDURE add_user_session_version()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sys_user'
          AND column_name = 'session_version'
    ) THEN
        ALTER TABLE sys_user
            ADD COLUMN session_version INT NOT NULL DEFAULT 0
            COMMENT '会话版本，重置密码时递增'
            AFTER password_hash;
    END IF;
END//
DELIMITER ;

CALL add_user_session_version();
DROP PROCEDURE IF EXISTS add_user_session_version;
