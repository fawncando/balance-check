-- 初始化数据（演示账号口令均为 123456）
USE balance_check;

INSERT IGNORE INTO sys_user (username, password_hash, display_name, role, card_no, status) VALUES
('admin', '$2a$10$soCI9jXm34hMePmA5ppKiu3V6kvjcnynjAQjmXNtLODb930Hz0aCS', '管理员', 'ADMIN', NULL, 'ACTIVE'),
('liyi',  '$2a$10$soCI9jXm34hMePmA5ppKiu3V6kvjcnynjAQjmXNtLODb930Hz0aCS', '李一', 'INSPECTOR', 'RY-001', 'ACTIVE'),
('lier',  '$2a$10$soCI9jXm34hMePmA5ppKiu3V6kvjcnynjAQjmXNtLODb930Hz0aCS', '李二', 'INSPECTOR', 'RY-002', 'ACTIVE'),
('qiyi',  '$2a$10$soCI9jXm34hMePmA5ppKiu3V6kvjcnynjAQjmXNtLODb930Hz0aCS', '齐一', 'REVIEWER', 'RY-003', 'ACTIVE');

INSERT IGNORE INTO weight_set (set_no, grade, note) VALUES
('FM2512920', 'F1', '220 g 天平用砝码盒：200 g / 50 g / 10 g'),
('FM2512921', 'F1', '双量程天平用砝码盒：100 g / 20 g / 2 g');

INSERT IGNORE INTO weight_set_item (weight_set_id, seq_no, nominal_g)
SELECT id, 1, 200.000000 FROM weight_set WHERE set_no = 'FM2512920';
INSERT IGNORE INTO weight_set_item (weight_set_id, seq_no, nominal_g)
SELECT id, 2, 50.000000 FROM weight_set WHERE set_no = 'FM2512920';
INSERT IGNORE INTO weight_set_item (weight_set_id, seq_no, nominal_g)
SELECT id, 3, 10.000000 FROM weight_set WHERE set_no = 'FM2512920';

INSERT IGNORE INTO weight_set_item (weight_set_id, seq_no, nominal_g)
SELECT id, 1, 100.000000 FROM weight_set WHERE set_no = 'FM2512921';
INSERT IGNORE INTO weight_set_item (weight_set_id, seq_no, nominal_g)
SELECT id, 2, 20.000000 FROM weight_set WHERE set_no = 'FM2512921';
INSERT IGNORE INTO weight_set_item (weight_set_id, seq_no, nominal_g)
SELECT id, 3, 2.000000 FROM weight_set WHERE set_no = 'FM2512921';

INSERT IGNORE INTO balance (code, unique_no, department, range_count, internal_calibration_mode, default_unit)
VALUES
('F001', 'DZTP170911C', '化学室', 1, '手动', 'g'),
('F002', 'SJS2605002', '化学室', 2, '手动', 'g');

INSERT IGNORE INTO balance_range (balance_id, seq_no, max_capacity_g, division_mg, weight_set_id)
SELECT b.id, 1, 220.000000, 0.100000, w.id
FROM balance b JOIN weight_set w ON w.set_no = 'FM2512920'
WHERE b.code = 'F001';

INSERT IGNORE INTO balance_range (balance_id, seq_no, max_capacity_g, division_mg, weight_set_id)
SELECT b.id, 1, 120.000000, 0.100000, w.id
FROM balance b JOIN weight_set w ON w.set_no = 'FM2512921'
WHERE b.code = 'F002';

INSERT IGNORE INTO balance_range (balance_id, seq_no, max_capacity_g, division_mg, weight_set_id)
SELECT b.id, 2, 40.000000, 0.010000, w.id
FROM balance b JOIN weight_set w ON w.set_no = 'FM2512921'
WHERE b.code = 'F002';
