-- Existing databases: add formal record voiding fields.
USE balance_check;

DROP PROCEDURE IF EXISTS add_calibration_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_calibration_column_if_missing(
    IN column_name_value VARCHAR(64),
    IN column_definition_value VARCHAR(500)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'calibration'
          AND column_name = column_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE calibration ADD COLUMN ', column_name_value,
                          ' ', column_definition_value);
        PREPARE statement_value FROM @ddl;
        EXECUTE statement_value;
        DEALLOCATE PREPARE statement_value;
    END IF;
END//
DELIMITER ;

CALL add_calibration_column_if_missing('voided_by', 'BIGINT NULL');
CALL add_calibration_column_if_missing('void_reason', 'VARCHAR(500) NULL');
CALL add_calibration_column_if_missing('voided_at', 'DATETIME NULL');

DROP PROCEDURE IF EXISTS add_calibration_void_index;
DELIMITER //
CREATE PROCEDURE add_calibration_void_index()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'calibration'
          AND index_name = 'idx_cal_voided_by'
    ) THEN
        ALTER TABLE calibration ADD KEY idx_cal_voided_by (voided_by);
    END IF;
END//
DELIMITER ;

CALL add_calibration_void_index();
DROP PROCEDURE IF EXISTS add_calibration_void_index;

DROP PROCEDURE IF EXISTS add_calibration_void_fk;
DELIMITER //
CREATE PROCEDURE add_calibration_void_fk()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE()
          AND table_name = 'calibration'
          AND constraint_name = 'fk_cal_voided_by'
    ) THEN
        ALTER TABLE calibration
            ADD CONSTRAINT fk_cal_voided_by
            FOREIGN KEY (voided_by) REFERENCES sys_user (id);
    END IF;
END//
DELIMITER ;

CALL add_calibration_void_fk();
DROP PROCEDURE IF EXISTS add_calibration_void_fk;
DROP PROCEDURE IF EXISTS add_calibration_column_if_missing;
