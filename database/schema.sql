-- 天平校验视频分析系统 数据库结构
-- 版本：v0.1（批次 3 框架搭建）
-- 说明：charset 统一 utf8mb4；时间统一存储为 DATETIME（Asia/Shanghai）

CREATE DATABASE IF NOT EXISTS balance_check
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE balance_check;

-- 用户与角色：ADMIN 管理员 / INSPECTOR 检验人员 / REVIEWER 复核确认人员
CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL COMMENT '登录账号',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 散列',
    display_name  VARCHAR(50)  NOT NULL COMMENT '姓名',
    role          VARCHAR(20)  NOT NULL COMMENT 'ADMIN/INSPECTOR/REVIEWER',
    card_no       VARCHAR(20)  NULL COMMENT '人员二维码卡号，用于与视频识别结果比对',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_card (card_no)
) ENGINE = InnoDB COMMENT = '用户';

-- 砝码组：一个编号对应一个砝码盒，盒内包含该台天平校验所需的全部砝码
CREATE TABLE IF NOT EXISTS weight_set (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    set_no      VARCHAR(20) NOT NULL COMMENT '砝码组编号（盒号）',
    grade       VARCHAR(20) NULL COMMENT '砝码等级',
    valid_until DATE        NULL COMMENT '有效期',
    note        VARCHAR(200) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_weight_set_no (set_no)
) ENGINE = InnoDB COMMENT = '砝码组';

CREATE TABLE IF NOT EXISTS weight_set_item (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    weight_set_id BIGINT        NOT NULL,
    seq_no        INT           NOT NULL COMMENT '组内序号 1..n',
    nominal_g     DECIMAL(18,6) NOT NULL COMMENT '标称质量（g）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_set_seq (weight_set_id, seq_no),
    CONSTRAINT fk_item_set FOREIGN KEY (weight_set_id) REFERENCES weight_set (id)
) ENGINE = InnoDB COMMENT = '砝码组明细';

-- 天平清单
CREATE TABLE IF NOT EXISTS balance (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    code                      VARCHAR(20)  NOT NULL COMMENT '天平编号，如 F001',
    unique_no                 VARCHAR(40)  NOT NULL COMMENT '设备唯一号',
    department                VARCHAR(50)  NOT NULL COMMENT '科室',
    range_count               INT          NOT NULL DEFAULT 1 COMMENT '量程数量 1 或 2',
    internal_calibration_mode VARCHAR(20)  NOT NULL COMMENT '自动/手动/NA',
    default_unit              VARCHAR(10)  NOT NULL DEFAULT 'g' COMMENT '默认显示单位，仅作识别兜底',
    status                    VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_balance_code (code),
    UNIQUE KEY uk_balance_unique_no (unique_no)
) ENGINE = InnoDB COMMENT = '天平清单';

CREATE TABLE IF NOT EXISTS balance_range (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    balance_id     BIGINT        NOT NULL,
    seq_no         INT           NOT NULL COMMENT '量程序号 1..n',
    max_capacity_g DECIMAL(18,6) NOT NULL COMMENT '量程上限（g）',
    division_mg    DECIMAL(18,6) NOT NULL COMMENT '分度值（mg）',
    weight_set_id  BIGINT        NOT NULL COMMENT '该台天平使用的砝码组',
    PRIMARY KEY (id),
    UNIQUE KEY uk_range_balance_seq (balance_id, seq_no),
    CONSTRAINT fk_range_balance FOREIGN KEY (balance_id) REFERENCES balance (id),
    CONSTRAINT fk_range_weight_set FOREIGN KEY (weight_set_id) REFERENCES weight_set (id)
) ENGINE = InnoDB COMMENT = '天平量程与精度';

-- 上传的视频文件：批次 3 的核心数据
CREATE TABLE IF NOT EXISTS video (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    stored_name   VARCHAR(255) NOT NULL COMMENT '服务器文件名（UUID）',
    relative_path VARCHAR(500) NOT NULL COMMENT '相对 uploads 的路径',
    size_bytes    BIGINT       NOT NULL,
    sha256        CHAR(64)     NULL COMMENT '文件摘要，用于去重与追溯',
    content_type  VARCHAR(100) NULL,
    uploader_id   BIGINT       NOT NULL,
    balance_id    BIGINT       NULL COMMENT '上传时选择的天平',
    uploaded_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status        VARCHAR(20)  NOT NULL DEFAULT 'UPLOADED'
                  COMMENT 'UPLOADED/ANALYZING/ANALYZED/FAILED',
    message       VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_video_uploader (uploader_id),
    KEY idx_video_balance (balance_id),
    CONSTRAINT fk_video_uploader FOREIGN KEY (uploader_id) REFERENCES sys_user (id),
    CONSTRAINT fk_video_balance FOREIGN KEY (balance_id) REFERENCES balance (id)
) ENGINE = InnoDB COMMENT = '校验视频';

-- 校验记录（表结构先建，批次 5 接入流程）
CREATE TABLE IF NOT EXISTS calibration (
    id                          BIGINT       NOT NULL AUTO_INCREMENT,
    record_no                   VARCHAR(32)  NOT NULL COMMENT '记录编号',
    balance_id                  BIGINT       NOT NULL,
    video_id                    BIGINT       NULL,
    inspector_id                BIGINT       NOT NULL,
    calibration_time            DATETIME     NOT NULL COMMENT '取视频开始拍摄时间，精确到分钟',
    clean_ok                    TINYINT(1)   NULL,
    level_ok                    TINYINT(1)   NULL,
    internal_calibration_result VARCHAR(10)  NULL COMMENT '是/否/NA',
    external_result             VARCHAR(10)  NULL COMMENT '是/否',
    remark                      VARCHAR(500) NULL DEFAULT '无',
    status                      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                                COMMENT 'DRAFT/SUBMITTED/REVIEWED',
    submitted_at                DATETIME     NULL,
    reviewed_at                 DATETIME     NULL,
    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_calibration_no (record_no),
    KEY idx_cal_balance_time (balance_id, calibration_time),
    KEY idx_cal_status (status),
    CONSTRAINT fk_cal_balance FOREIGN KEY (balance_id) REFERENCES balance (id),
    CONSTRAINT fk_cal_video FOREIGN KEY (video_id) REFERENCES video (id),
    CONSTRAINT fk_cal_inspector FOREIGN KEY (inspector_id) REFERENCES sys_user (id)
) ENGINE = InnoDB COMMENT = '校验记录';

CREATE TABLE IF NOT EXISTS calibration_measurement (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    calibration_id     BIGINT        NOT NULL,
    range_seq          INT           NOT NULL DEFAULT 1,
    weight_seq         INT           NOT NULL COMMENT '第几个砝码 1..3',
    measure_seq        INT           NOT NULL COMMENT '该砝码第几次称量 1..3',
    nominal_g          DECIMAL(18,6) NOT NULL,
    lower_g            DECIMAL(18,6) NOT NULL,
    upper_g            DECIMAL(18,6) NOT NULL,
    display_value      DECIMAL(18,6) NOT NULL COMMENT '天平实际显示的数值',
    display_unit       VARCHAR(10)   NOT NULL COMMENT 'g 或 mg，以天平显示为准',
    value_g            DECIMAL(18,6) NOT NULL COMMENT '统一换算为 g 后的示值',
    measured_at        DATETIME      NOT NULL COMMENT '测量时间，精确到分钟',
    video_second       INT           NULL COMMENT '视频时间点（秒）',
    qualified          TINYINT(1)    NULL,
    confidence         DECIMAL(5,2)  NULL,
    manually_corrected TINYINT(1)    NOT NULL DEFAULT 0,
    raw_text           VARCHAR(100)  NULL COMMENT '原始识别文本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_measure (calibration_id, range_seq, weight_seq, measure_seq),
    CONSTRAINT fk_measure_cal FOREIGN KEY (calibration_id) REFERENCES calibration (id)
) ENGINE = InnoDB COMMENT = '称量明细';

CREATE TABLE IF NOT EXISTS review (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    calibration_id BIGINT       NOT NULL,
    reviewer_id    BIGINT       NOT NULL,
    reviewed_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    result         VARCHAR(20)  NOT NULL COMMENT 'APPROVED/REJECTED',
    comment        VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_cal (calibration_id),
    CONSTRAINT fk_review_cal FOREIGN KEY (calibration_id) REFERENCES calibration (id),
    CONSTRAINT fk_review_user FOREIGN KEY (reviewer_id) REFERENCES sys_user (id)
) ENGINE = InnoDB COMMENT = '复核记录';

CREATE TABLE IF NOT EXISTS record_template (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    version    VARCHAR(20)  NOT NULL,
    content    JSON         NULL COMMENT '模板结构',
    creator_id BIGINT       NULL,
    enabled    TINYINT(1)   NOT NULL DEFAULT 1,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_name_version (name, version)
) ENGINE = InnoDB COMMENT = '记录表模板';

CREATE TABLE IF NOT EXISTS audit_log (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    user_id     BIGINT        NULL,
    action      VARCHAR(50)   NOT NULL,
    target_type VARCHAR(30)   NULL,
    target_id   VARCHAR(50)   NULL,
    detail      VARCHAR(1000) NULL,
    ip          VARCHAR(64)   NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_user_time (user_id, created_at)
) ENGINE = InnoDB COMMENT = '操作日志';
