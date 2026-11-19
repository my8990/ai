CREATE DATABASE IF NOT EXISTS ai_assistant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ai_assistant;

CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(50)  NOT NULL COMMENT '用户名/手机号/学号',
    `password`    VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'student' COMMENT '角色: student/teacher/admin',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `student_no`  VARCHAR(50)  DEFAULT NULL COMMENT '学号',
    `college`     VARCHAR(100) DEFAULT NULL COMMENT '所属学院(教师)',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0禁用',
    `ban_reason`  VARCHAR(500) DEFAULT NULL COMMENT '禁用原因',
    `approved`    TINYINT      NOT NULL DEFAULT 0 COMMENT '审核: 1通过 0待审核(教师)',
    `openid`      VARCHAR(64)  DEFAULT NULL COMMENT '微信openid',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删除 1已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_phone` (`phone`),
    UNIQUE KEY `uk_student_no` (`student_no`),
    UNIQUE KEY `uk_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

INSERT IGNORE INTO `user` (`username`, `password`, `nickname`, `role`, `status`, `approved`) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'admin', 1, 1);

CREATE TABLE IF NOT EXISTS `course` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`          VARCHAR(100) NOT NULL COMMENT '课程名称',
    `semester`      VARCHAR(50)  NOT NULL COMMENT '学期',
    `college`       VARCHAR(100) DEFAULT NULL COMMENT '学院',
    `description`   TEXT         DEFAULT NULL COMMENT '课程简介',
    `cover`         VARCHAR(255) DEFAULT NULL COMMENT '课程封面图片URL',
    `code`          VARCHAR(6)   NOT NULL COMMENT '课程加课码(6位字母+数字)',
    `status`        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1开放 0关闭',
    `teacher_id`    BIGINT       NOT NULL COMMENT '创建教师ID',
    `teacher_name`  VARCHAR(50)  DEFAULT NULL COMMENT '创建教师姓名',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删除 1已删除',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';

CREATE TABLE IF NOT EXISTS `course_student` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `course_id`   BIGINT   NOT NULL COMMENT '课程ID',
    `student_id`  BIGINT   NOT NULL COMMENT '学生ID',
    `status`      TINYINT  NOT NULL DEFAULT 1 COMMENT '状态: 1正常 0已退课',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_student` (`course_id`, `student_id`),
    KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程学生关联表';

CREATE TABLE IF NOT EXISTS `appeal` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT '申诉用户ID',
    `reason`      VARCHAR(500) NOT NULL COMMENT '申诉理由',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0待处理 1通过 2驳回',
    `reply`       VARCHAR(500) DEFAULT NULL COMMENT '管理员回复',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='申诉表';

CREATE TABLE IF NOT EXISTS `checkin` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `course_id`   BIGINT       NOT NULL COMMENT '课程ID',
    `title`       VARCHAR(100) NOT NULL COMMENT '签到标题',
    `type`        TINYINT      NOT NULL DEFAULT 1 COMMENT '签到类型: 1数字签到 2二维码签到 3扫码或数字',
    `late_minutes` INT         NOT NULL DEFAULT 0 COMMENT '超过多少分钟记为迟到(0表示不判定迟到)',
    `code`        VARCHAR(6)   DEFAULT NULL COMMENT '数字签到码(6位数字)',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1进行中 0已结束',
    `teacher_id`  BIGINT       NOT NULL COMMENT '发布教师ID',
    `expire_time` DATETIME     DEFAULT NULL COMMENT '过期时间(NULL表示手动结束)',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删除 1已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `active_flag` TINYINT GENERATED ALWAYS AS (IF(`status` = 1, 1, NULL)) STORED COMMENT '进行中标记: 进行中为1 已结束为NULL',
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_teacher_id` (`teacher_id`),
    UNIQUE KEY `uk_course_active` (`course_id`, `active_flag`),
    UNIQUE KEY `uk_code_active` (`code`, `active_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='签到活动表';

CREATE TABLE IF NOT EXISTS `checkin_record` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `checkin_id`  BIGINT   NOT NULL COMMENT '签到活动ID',
    `student_id`  BIGINT   NOT NULL COMMENT '学生ID',
    `status`      TINYINT  NOT NULL DEFAULT 1 COMMENT '签到状态: 1出勤 2迟到 3请假 4缺勤',
    `latitude`    DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
    `longitude`   DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
    `address`     VARCHAR(200) DEFAULT NULL COMMENT '签到地点',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签到时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_checkin_student` (`checkin_id`, `student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='签到记录表';

CREATE TABLE IF NOT EXISTS `homework` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `course_id`   BIGINT       NOT NULL COMMENT '课程ID',
    `title`       VARCHAR(100) NOT NULL COMMENT '作业标题',
    `description` TEXT         DEFAULT NULL COMMENT '作业要求/描述',
    `full_score`  DECIMAL(5,2) NOT NULL DEFAULT 100 COMMENT '满分分值',
    `ref_file_url` VARCHAR(500) DEFAULT NULL COMMENT '参考文件URL',
    `ref_file_name` VARCHAR(200) DEFAULT NULL COMMENT '参考文件名',
    `deadline`    DATETIME     DEFAULT NULL COMMENT '截止时间(NULL表示无截止)',
    `teacher_id`  BIGINT       NOT NULL COMMENT '发布教师ID',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删除 1已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作业表';

CREATE TABLE IF NOT EXISTS `homework_submit` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `homework_id` BIGINT       NOT NULL COMMENT '作业ID',
    `student_id`  BIGINT       NOT NULL COMMENT '学生ID',
    `content`     TEXT         DEFAULT NULL COMMENT '文字内容',
    `images`      TEXT         DEFAULT NULL COMMENT '图片URL列表(逗号分隔)',
    `file_url`    VARCHAR(500) DEFAULT NULL COMMENT '文件URL',
    `file_name`   VARCHAR(200) DEFAULT NULL COMMENT '文件名',
    `score`       DECIMAL(5,2) DEFAULT NULL COMMENT '分数(0-100)',
    `comment`     VARCHAR(500) DEFAULT NULL COMMENT '教师评语',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0已提交 1已批改',
    `is_late`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否迟交: 0否 1是',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_homework_student` (`homework_id`, `student_id`),
    KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作业提交表';

CREATE TABLE IF NOT EXISTS `score_rule` (
    `id`                BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `course_id`         BIGINT   NOT NULL COMMENT '课程ID',
    `checkin_weight`    INT      NOT NULL DEFAULT 50 COMMENT '签到占比(0-100)',
    `homework_weight`   INT      NOT NULL DEFAULT 50 COMMENT '作业占比(0-100)',
    `quiz_weight`       INT      NOT NULL DEFAULT 0 COMMENT '测验占比(0-100)',
    `create_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成绩规则表';

SET @score_rule_quiz_weight_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `score_rule` ADD COLUMN `quiz_weight` INT NOT NULL DEFAULT 0 COMMENT ''测验占比(0-100)'' AFTER `homework_weight`',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'score_rule' AND column_name = 'quiz_weight'
);
PREPARE score_rule_quiz_weight_stmt FROM @score_rule_quiz_weight_ddl;
EXECUTE score_rule_quiz_weight_stmt;
DEALLOCATE PREPARE score_rule_quiz_weight_stmt;

CREATE TABLE IF NOT EXISTS `material` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `course_id`   BIGINT       NOT NULL COMMENT '课程ID',
    `title`       VARCHAR(100) NOT NULL COMMENT '课件标题',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '课件说明',
    `file_url`    VARCHAR(500) NOT NULL COMMENT '文件URL',
    `file_name`   VARCHAR(200) DEFAULT NULL COMMENT '原始文件名',
    `file_size`   BIGINT       DEFAULT NULL COMMENT '文件大小(字节)',
    `file_type`   VARCHAR(20)  DEFAULT NULL COMMENT '文件类型: pdf/image/ppt/word/other',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1上架 0下架',
    `teacher_id`  BIGINT       NOT NULL COMMENT '上传教师ID',
    `live_session_id` BIGINT   DEFAULT NULL COMMENT '来源课堂ID(下课后自动发布时写入)',
    `source`      TINYINT      NOT NULL DEFAULT 1 COMMENT '来源: 1教师手动上传 2课堂课件发布',
    `page_count`  INT          DEFAULT NULL COMMENT '课件页数',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删除 1已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_live_session` (`live_session_id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课件资源表';

CREATE TABLE IF NOT EXISTS `post` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `course_id`     BIGINT   NOT NULL COMMENT '课程ID',
    `author_id`     BIGINT   NOT NULL COMMENT '发帖人ID',
    `content`       TEXT     NOT NULL COMMENT '帖子内容',
    `images`        TEXT     DEFAULT NULL COMMENT '图片URL列表(逗号分隔)',
    `like_count`    INT      NOT NULL DEFAULT 0 COMMENT '点赞数',
    `comment_count` INT      NOT NULL DEFAULT 0 COMMENT '评论数',
    `deleted`       TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删除 1已删除',
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_author_id` (`author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级讨论帖子表';

CREATE TABLE IF NOT EXISTS `post_comment` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `post_id`     BIGINT   NOT NULL COMMENT '帖子ID',
    `author_id`   BIGINT   NOT NULL COMMENT '评论人ID',
    `content`     VARCHAR(1000) NOT NULL COMMENT '评论内容',
    `deleted`     TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删除 1已删除',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    PRIMARY KEY (`id`),
    KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子评论表';

CREATE TABLE IF NOT EXISTS `post_like` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `post_id`     BIGINT   NOT NULL COMMENT '帖子ID',
    `user_id`     BIGINT   NOT NULL COMMENT '点赞人ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_user` (`post_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子点赞表';

CREATE TABLE IF NOT EXISTS `notice` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `course_id`   BIGINT       NOT NULL COMMENT '课程ID',
    `type`        TINYINT      NOT NULL DEFAULT 1 COMMENT '类型: 1课堂课件发布 2教师公告',
    `title`       VARCHAR(100) NOT NULL COMMENT '公告标题',
    `content`     VARCHAR(500) DEFAULT NULL COMMENT '公告内容',
    `session_id`  BIGINT       DEFAULT NULL COMMENT '关联课堂ID',
    `material_id` BIGINT       DEFAULT NULL COMMENT '关联课件ID',
    `teacher_id`  BIGINT       NOT NULL COMMENT '发布教师ID',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删除 1已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_type` (`session_id`, `type`),
    KEY `idx_course_time` (`course_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程公告表';

CREATE TABLE IF NOT EXISTS `live_session` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `course_id`    BIGINT       NOT NULL COMMENT '课程ID',
    `teacher_id`   BIGINT       NOT NULL COMMENT '授课教师ID',
    `checkin_id`   BIGINT       DEFAULT NULL COMMENT '关联的自动签到活动ID',
    `quiz_assignment_id` BIGINT DEFAULT NULL COMMENT '本节课进行中的随堂测验发布ID',
    `join_code`    VARCHAR(6)   NOT NULL COMMENT '课堂码(6位)',
    `title`        VARCHAR(100) NOT NULL COMMENT '课堂标题',
    `current_page` INT          NOT NULL DEFAULT 0 COMMENT '当前课件页码(0表示无课件)',
    `slide_total`  INT          NOT NULL DEFAULT 0 COMMENT '课件总页数',
    `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1上课中 0已下课',
    `end_time`     DATETIME     DEFAULT NULL COMMENT '下课时间',
    `enter_count`  INT          NOT NULL DEFAULT 0 COMMENT '累计进入课堂人数(下课时归档)',
    `original_doc_url`  VARCHAR(500) DEFAULT NULL COMMENT '教师上传的原始课件文件URL',
    `original_file_name` VARCHAR(200) DEFAULT NULL COMMENT '原始课件文件名(含扩展名)',
    `courseware_status` TINYINT  NOT NULL DEFAULT 0 COMMENT '课件发布状态: 0未发布 1已发布 2发布失败',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删除 1已删除',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开课时间',
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_join_code` (`join_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课堂会话表';

SET @live_session_quiz_assignment_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `live_session` ADD COLUMN `quiz_assignment_id` BIGINT DEFAULT NULL COMMENT ''本节课进行中的随堂测验发布ID'' AFTER `checkin_id`',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'live_session' AND column_name = 'quiz_assignment_id'
);
PREPARE live_session_quiz_assignment_stmt FROM @live_session_quiz_assignment_ddl;
EXECUTE live_session_quiz_assignment_stmt;
DEALLOCATE PREPARE live_session_quiz_assignment_stmt;

-- 进行中的课堂码全局唯一，避免并发开课时生成相同课堂码
-- 生成列 active_flag：进行中为1，已结束或已删除为NULL，使历史课堂码可复用
SET @live_join_active_flag_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `live_session` ADD COLUMN `active_flag` TINYINT GENERATED ALWAYS AS (IF(`status` = 1 AND `deleted` = 0, 1, NULL)) STORED COMMENT ''进行中标记: 进行中为1 已结束或删除为NULL''',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'live_session' AND column_name = 'active_flag'
);
PREPARE live_join_active_flag_stmt FROM @live_join_active_flag_ddl;
EXECUTE live_join_active_flag_stmt;
DEALLOCATE PREPARE live_join_active_flag_stmt;

-- 仅当现有数据没有重复的进行中课堂码时才添加唯一索引，避免升级时启动失败
SET @live_join_code_dup = (
    SELECT COUNT(*) FROM (
        SELECT `join_code` FROM `live_session`
        WHERE `status` = 1 AND `deleted` = 0
        GROUP BY `join_code` HAVING COUNT(*) > 1
    ) t
);
SET @live_join_code_uk_ddl = (
    SELECT IF(@live_join_code_dup = 0
              AND (SELECT COUNT(*) FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'live_session'
                     AND index_name = 'uk_join_code_active') = 0,
              'ALTER TABLE `live_session` ADD UNIQUE KEY `uk_join_code_active` (`join_code`, `active_flag`)',
              'DO 0')
);
PREPARE live_join_code_uk_stmt FROM @live_join_code_uk_ddl;
EXECUTE live_join_code_uk_stmt;
DEALLOCATE PREPARE live_join_code_uk_stmt;

CREATE TABLE IF NOT EXISTS `live_slide` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`  BIGINT       NOT NULL COMMENT '课堂会话ID',
    `page_no`     INT          NOT NULL COMMENT '页码(从1开始)',
    `image_url`   VARCHAR(500) NOT NULL COMMENT '课件图片URL',
    `file_name`   VARCHAR(200) DEFAULT NULL COMMENT '原始文件名',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_page` (`session_id`, `page_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课堂课件页表';

CREATE TABLE IF NOT EXISTS `live_presence` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`     BIGINT      NOT NULL COMMENT '课堂会话ID',
    `user_id`        BIGINT      NOT NULL COMMENT '用户ID',
    `role`           VARCHAR(20) NOT NULL COMMENT '角色: teacher/student',
    `last_heartbeat` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次心跳时间',
    `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次进入课堂时间',
    `update_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_user` (`session_id`, `user_id`),
    KEY `idx_session_heartbeat` (`session_id`, `last_heartbeat`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课堂在线状态表';

CREATE TABLE IF NOT EXISTS `live_interaction` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`  BIGINT       NOT NULL COMMENT '课堂会话ID',
    `user_id`     BIGINT       NOT NULL COMMENT '发起学生ID',
    `type`        VARCHAR(20)  NOT NULL COMMENT '类型: hand举手 question提问',
    `content`     VARCHAR(500) DEFAULT NULL COMMENT '提问内容(举手时为空)',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0待处理 1已处理',
    `handle_time` DATETIME     DEFAULT NULL COMMENT '处理时间',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_session_status` (`session_id`, `status`),
    KEY `idx_session_user_type` (`session_id`, `user_id`, `type`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课堂互动表(举手/提问)';

CREATE TABLE IF NOT EXISTS `live_ws_ticket` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ticket`      VARCHAR(64) NOT NULL COMMENT '一次性连接凭证',
    `session_id`  BIGINT      NOT NULL COMMENT '课堂会话ID',
    `user_id`     BIGINT      NOT NULL COMMENT '用户ID',
    `role`        VARCHAR(20) NOT NULL COMMENT '角色: teacher/student',
    `expire_time` DATETIME    NOT NULL COMMENT '过期时间',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticket` (`ticket`),
    KEY `idx_expire_time` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课堂WebSocket一次性连接凭证';

CREATE TABLE IF NOT EXISTS `live_join_guard` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`  BIGINT   NOT NULL COMMENT '课堂会话ID',
    `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
    `failures`    INT      NOT NULL DEFAULT 0 COMMENT '连续错误次数',
    `lock_until`  DATETIME DEFAULT NULL COMMENT '锁定截止时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_user` (`session_id`, `user_id`),
    KEY `idx_update_time` (`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课堂码防暴力枚举记录(多实例共享)';

CREATE TABLE IF NOT EXISTS `live_broadcast_event` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`    BIGINT      NOT NULL COMMENT '课堂会话ID',
    `force_refresh` TINYINT     NOT NULL DEFAULT 1 COMMENT '是否强制刷新状态缓存',
    `instance_id`   VARCHAR(64) NOT NULL DEFAULT '' COMMENT '产生事件的实例标识',
    `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课堂广播事件表(跨实例分发)';

CREATE TABLE IF NOT EXISTS `audit_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`       BIGINT       DEFAULT NULL COMMENT '操作人ID(未登录为空)',
    `username`      VARCHAR(50)  DEFAULT NULL COMMENT '操作人用户名',
    `role`          VARCHAR(20)  DEFAULT NULL COMMENT '操作人角色',
    `module`        VARCHAR(50)  NOT NULL COMMENT '业务模块',
    `action`        VARCHAR(50)  NOT NULL COMMENT '操作动作',
    `method`        VARCHAR(10)  NOT NULL COMMENT 'HTTP方法',
    `uri`           VARCHAR(255) NOT NULL COMMENT '请求路径',
    `http_status`   INT          NOT NULL COMMENT '响应状态码',
    `success`       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功: 1成功 0失败',
    `error_message` VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
    `ip`            VARCHAR(64)  DEFAULT NULL COMMENT '客户端IP',
    `duration_ms`   BIGINT       NOT NULL DEFAULT 0 COMMENT '耗时(毫秒)',
    `trace_id`      VARCHAR(64)  DEFAULT NULL COMMENT '链路追踪ID',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_audit_user` (`user_id`),
    KEY `idx_audit_create_time` (`create_time`),
    KEY `idx_audit_module_action` (`module`, `action`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作审计日志表';

CREATE TABLE IF NOT EXISTS `quiz` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `course_id`           BIGINT       DEFAULT NULL COMMENT '创建来源课程ID(仅作记录,可为空)',
    `title`               VARCHAR(100) NOT NULL COMMENT '测验标题',
    `description`         TEXT         DEFAULT NULL COMMENT '测验说明',
    `full_score`          DECIMAL(5,2) NOT NULL DEFAULT 100 COMMENT '满分分值',
    `duration_minutes`    INT          NOT NULL DEFAULT 0 COMMENT '默认限时分钟数(0表示不限时)',
    `start_time`          DATETIME     DEFAULT NULL COMMENT '默认开放开始时间(NULL表示不限)',
    `end_time`            DATETIME     DEFAULT NULL COMMENT '默认开放截止时间(NULL表示不限)',
    `show_answer_after`   TINYINT      NOT NULL DEFAULT 1 COMMENT '提交后是否公布答案与解析: 1是 0否',
    `teacher_id`          BIGINT       NOT NULL COMMENT '创建教师ID',
    `source_quiz_id`      BIGINT       DEFAULT NULL COMMENT '若为引用复制而来,记录原始测验ID',
    `status`              TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0草稿(可编辑) 1已就绪(已用于发布)',
    `visibility`          TINYINT      NOT NULL DEFAULT 0 COMMENT '可见性: 0私有 1对教师公开',
    `favorite_count`      INT          NOT NULL DEFAULT 0 COMMENT '被收藏次数',
    `use_count`           INT          NOT NULL DEFAULT 0 COMMENT '被引用次数',
    `shared_time`         DATETIME     DEFAULT NULL COMMENT '设为公开的时间',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删除 1已删除',
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_quiz_course` (`course_id`),
    KEY `idx_quiz_teacher` (`teacher_id`),
    KEY `idx_quiz_visibility` (`visibility`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测验题库表(教师个人测验模板)';

CREATE TABLE IF NOT EXISTS `quiz_question` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `quiz_id`        BIGINT       NOT NULL COMMENT '测验ID',
    `type`           TINYINT      NOT NULL DEFAULT 1 COMMENT '题型: 1单选 2多选 3判断 4填空',
    `content`        TEXT         NOT NULL COMMENT '题干',
    `options`        TEXT         DEFAULT NULL COMMENT '选项列表(JSON数组,填空题为空)',
    `correct_answer` VARCHAR(500) NOT NULL COMMENT '正确答案(选择题存选项下标,多选逗号分隔,填空存文本)',
    `score`          DECIMAL(5,2) NOT NULL DEFAULT 5 COMMENT '本题分值',
    `analysis`       VARCHAR(1000) DEFAULT NULL COMMENT '答案解析',
    `sort_order`     INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_question_quiz` (`quiz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测验题目表';

CREATE TABLE IF NOT EXISTS `quiz_assignment` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `quiz_id`          BIGINT       NOT NULL COMMENT '测验模板ID',
    `course_id`        BIGINT       NOT NULL COMMENT '发布到的课程ID',
    `teacher_id`       BIGINT       NOT NULL COMMENT '发布教师ID',
    `title`            VARCHAR(100) NOT NULL COMMENT '发布标题(默认取模板标题)',
    `duration_minutes` INT          NOT NULL DEFAULT 0 COMMENT '限时分钟数(0表示不限时)',
    `start_time`       DATETIME     DEFAULT NULL COMMENT '开放开始时间(NULL表示不限)',
    `end_time`         DATETIME     DEFAULT NULL COMMENT '开放截止时间(NULL表示不限)',
    `status`           TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0待发布 1进行中 2已结束',
    `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0未删除 1已删除',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `uk_quiz_course` (`quiz_id`, `course_id`),
    KEY `idx_assign_course` (`course_id`),
    KEY `idx_assign_teacher` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测验发布实例表';

SET @quiz_assignment_uk_ddl = (
    SELECT IF(COUNT(*) > 0 AND MAX(NON_UNIQUE) = 0,
              'ALTER TABLE `quiz_assignment` DROP INDEX `uk_quiz_course`, ADD INDEX `uk_quiz_course` (`quiz_id`, `course_id`)',
              'DO 0')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'quiz_assignment' AND index_name = 'uk_quiz_course'
);
PREPARE quiz_assignment_uk_stmt FROM @quiz_assignment_uk_ddl;
EXECUTE quiz_assignment_uk_stmt;
DEALLOCATE PREPARE quiz_assignment_uk_stmt;


CREATE TABLE IF NOT EXISTS `quiz_favorite` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `teacher_id`  BIGINT   NOT NULL COMMENT '教师ID',
    `quiz_id`     BIGINT   NOT NULL COMMENT '测验模板ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_teacher_quiz` (`teacher_id`, `quiz_id`),
    KEY `idx_favorite_quiz` (`quiz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师收藏测验表';

CREATE TABLE IF NOT EXISTS `quiz_record` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `quiz_id`       BIGINT       NOT NULL COMMENT '测验模板ID',
    `assignment_id` BIGINT       DEFAULT NULL COMMENT '发布实例ID(为空表示旧数据)',
    `student_id`    BIGINT       NOT NULL COMMENT '学生ID',
    `score`         DECIMAL(5,2) DEFAULT NULL COMMENT '得分',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '状态: 0答题中 1已提交',
    `start_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始答题时间(服务端时间,用于倒计时)',
    `submit_at`     DATETIME     DEFAULT NULL COMMENT '提交时间',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_assign_student` (`assignment_id`, `student_id`),
    KEY `idx_record_student` (`student_id`),
    KEY `idx_record_quiz` (`quiz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测验答卷表';

CREATE TABLE IF NOT EXISTS `quiz_answer` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `record_id`   BIGINT       NOT NULL COMMENT '答卷ID',
    `question_id` BIGINT       NOT NULL COMMENT '题目ID',
    `answer`      VARCHAR(1000) DEFAULT NULL COMMENT '学生作答内容',
    `is_correct`  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否正确: 1正确 0错误',
    `score`       DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '本题得分',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_question` (`record_id`, `question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测验作答明细表';

-- 如果已有数据库，执行以下语句补齐新增字段：
-- ALTER TABLE `user` ADD COLUMN `ban_reason` VARCHAR(500) DEFAULT NULL COMMENT '禁用原因' AFTER `status`;
-- ALTER TABLE `checkin` ADD COLUMN `late_minutes` INT DEFAULT 0 COMMENT '超过多少分钟记为迟到(0表示不判定迟到)' AFTER `type`;
-- ALTER TABLE `checkin_record` ADD COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT '签到状态: 1正常 2迟到' AFTER `student_id`;
-- ALTER TABLE `checkin_record` ADD COLUMN `latitude` DECIMAL(10,6) DEFAULT NULL COMMENT '纬度' AFTER `status`;
-- ALTER TABLE `checkin_record` ADD COLUMN `longitude` DECIMAL(10,6) DEFAULT NULL COMMENT '经度' AFTER `latitude`;
-- ALTER TABLE `checkin_record` ADD COLUMN `address` VARCHAR(200) DEFAULT NULL COMMENT '签到地点' AFTER `longitude`;
-- ALTER TABLE `homework` ADD COLUMN `full_score` DECIMAL(5,2) NOT NULL DEFAULT 100 COMMENT '满分分值' AFTER `description`;
-- ALTER TABLE `homework` ADD COLUMN `ref_file_url` VARCHAR(500) DEFAULT NULL COMMENT '参考文件URL' AFTER `full_score`;
-- ALTER TABLE `homework` ADD COLUMN `ref_file_name` VARCHAR(200) DEFAULT NULL COMMENT '参考文件名' AFTER `ref_file_url`;
-- ALTER TABLE `homework_submit` ADD COLUMN `is_late` TINYINT NOT NULL DEFAULT 0 COMMENT '是否迟交: 0否 1是' AFTER `status`;
-- 同一课程只允许存在一个进行中的签到：先结束历史遗留的重复进行中签到，再补列与唯一索引
-- UPDATE `checkin` c JOIN (SELECT `course_id`, MAX(`id`) AS keep_id FROM `checkin` WHERE `status` = 1 AND `deleted` = 0 GROUP BY `course_id` HAVING COUNT(*) > 1) t ON c.`course_id` = t.`course_id` SET c.`status` = 0 WHERE c.`status` = 1 AND c.`deleted` = 0 AND c.`id` <> t.keep_id;
-- ALTER TABLE `checkin` ADD COLUMN `active_flag` TINYINT GENERATED ALWAYS AS (IF(`status` = 1, 1, NULL)) STORED COMMENT '进行中标记: 进行中为1 已结束为NULL';
-- ALTER TABLE `checkin` ADD UNIQUE KEY `uk_course_active` (`course_id`, `active_flag`);
-- 进行中的签到码全局唯一，避免学生按码签到时命中错误的课程
-- ALTER TABLE `checkin` ADD UNIQUE KEY `uk_code_active` (`code`, `active_flag`);
-- 课堂历史与在线状态（已有数据库补齐）
-- ALTER TABLE `live_session` ADD COLUMN `end_time` DATETIME DEFAULT NULL COMMENT '下课时间' AFTER `status`;
-- 上面的 ALTER 无法幂等执行，这里用动态 SQL 自动补齐 end_time，重复启动不会报错
SET @live_end_time_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `live_session` ADD COLUMN `end_time` DATETIME DEFAULT NULL COMMENT ''下课时间'' AFTER `status`',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'live_session'
      AND column_name = 'end_time'
);
PREPARE live_end_time_stmt FROM @live_end_time_ddl;
EXECUTE live_end_time_stmt;
DEALLOCATE PREPARE live_end_time_stmt;

-- 课堂累计进入人数归档字段（下课后写入，用于课后报告，替代长期保留 live_presence）
SET @live_enter_count_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `live_session` ADD COLUMN `enter_count` INT NOT NULL DEFAULT 0 COMMENT ''累计进入课堂人数(下课时归档)'' AFTER `end_time`',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'live_session'
      AND column_name = 'enter_count'
);
PREPARE live_enter_count_stmt FROM @live_enter_count_ddl;
EXECUTE live_enter_count_stmt;
DEALLOCATE PREPARE live_enter_count_stmt;

-- 课堂课件发布：记录教师上传的原始文档（PPT/Word/PDF/图片），用于下课后按原格式发布
SET @live_original_doc_url_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `live_session` ADD COLUMN `original_doc_url` VARCHAR(500) DEFAULT NULL COMMENT ''教师上传的原始课件文件URL'' AFTER `enter_count`',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'live_session'
      AND column_name = 'original_doc_url'
);
PREPARE live_original_doc_url_stmt FROM @live_original_doc_url_ddl;
EXECUTE live_original_doc_url_stmt;
DEALLOCATE PREPARE live_original_doc_url_stmt;

SET @live_original_file_name_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `live_session` ADD COLUMN `original_file_name` VARCHAR(200) DEFAULT NULL COMMENT ''原始课件文件名(含扩展名)'' AFTER `original_doc_url`',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'live_session'
      AND column_name = 'original_file_name'
);
PREPARE live_original_file_name_stmt FROM @live_original_file_name_ddl;
EXECUTE live_original_file_name_stmt;
DEALLOCATE PREPARE live_original_file_name_stmt;

SET @live_courseware_status_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `live_session` ADD COLUMN `courseware_status` TINYINT NOT NULL DEFAULT 0 COMMENT ''课件发布状态: 0未发布 1已发布 2发布失败'' AFTER `original_file_name`',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'live_session'
      AND column_name = 'courseware_status'
);
PREPARE live_courseware_status_stmt FROM @live_courseware_status_ddl;
EXECUTE live_courseware_status_stmt;
DEALLOCATE PREPARE live_courseware_status_stmt;

-- 课堂课件发布：material 记录来源课堂，保证同一课堂只发布一次
SET @material_live_session_id_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `material` ADD COLUMN `live_session_id` BIGINT DEFAULT NULL COMMENT ''来源课堂ID(下课后自动发布时写入)'' AFTER `teacher_id`',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'material'
      AND column_name = 'live_session_id'
);
PREPARE material_live_session_id_stmt FROM @material_live_session_id_ddl;
EXECUTE material_live_session_id_stmt;
DEALLOCATE PREPARE material_live_session_id_stmt;

SET @material_source_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `material` ADD COLUMN `source` TINYINT NOT NULL DEFAULT 1 COMMENT ''来源: 1教师手动上传 2课堂课件发布'' AFTER `live_session_id`',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'material'
      AND column_name = 'source'
);
PREPARE material_source_stmt FROM @material_source_ddl;
EXECUTE material_source_stmt;
DEALLOCATE PREPARE material_source_stmt;

SET @material_page_count_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `material` ADD COLUMN `page_count` INT DEFAULT NULL COMMENT ''课件页数'' AFTER `source`',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'material'
      AND column_name = 'page_count'
);
PREPARE material_page_count_stmt FROM @material_page_count_ddl;
EXECUTE material_page_count_stmt;
DEALLOCATE PREPARE material_page_count_stmt;

SET @material_uk_live_session_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `material` ADD UNIQUE KEY `uk_live_session` (`live_session_id`)',
              'DO 0')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'material'
      AND index_name = 'uk_live_session'
);
PREPARE material_uk_live_session_stmt FROM @material_uk_live_session_ddl;
EXECUTE material_uk_live_session_stmt;
DEALLOCATE PREPARE material_uk_live_session_stmt;

-- ==================== 测验改造：题库模板 + 发布实例 + 教师共享 ====================
-- quiz 由「课程测验」升级为「教师个人测验模板」，course_id 仅作创建来源记录
ALTER TABLE `quiz` MODIFY COLUMN `course_id` BIGINT DEFAULT NULL COMMENT '创建来源课程ID(仅作记录,可为空)';
ALTER TABLE `quiz` MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0草稿(可编辑) 1已就绪(已用于发布)';
ALTER TABLE `quiz` MODIFY COLUMN `teacher_id` BIGINT NOT NULL COMMENT '创建教师ID';

SET @quiz_source_quiz_id_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `quiz` ADD COLUMN `source_quiz_id` BIGINT DEFAULT NULL COMMENT ''若为引用复制而来,记录原始测验ID'' AFTER `teacher_id`',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'quiz' AND column_name = 'source_quiz_id'
);
PREPARE quiz_source_quiz_id_stmt FROM @quiz_source_quiz_id_ddl;
EXECUTE quiz_source_quiz_id_stmt;
DEALLOCATE PREPARE quiz_source_quiz_id_stmt;

SET @quiz_visibility_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `quiz` ADD COLUMN `visibility` TINYINT NOT NULL DEFAULT 0 COMMENT ''可见性: 0私有 1对教师公开'' AFTER `status`',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'quiz' AND column_name = 'visibility'
);
PREPARE quiz_visibility_stmt FROM @quiz_visibility_ddl;
EXECUTE quiz_visibility_stmt;
DEALLOCATE PREPARE quiz_visibility_stmt;

SET @quiz_favorite_count_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `quiz` ADD COLUMN `favorite_count` INT NOT NULL DEFAULT 0 COMMENT ''被收藏次数'' AFTER `visibility`',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'quiz' AND column_name = 'favorite_count'
);
PREPARE quiz_favorite_count_stmt FROM @quiz_favorite_count_ddl;
EXECUTE quiz_favorite_count_stmt;
DEALLOCATE PREPARE quiz_favorite_count_stmt;

SET @quiz_use_count_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `quiz` ADD COLUMN `use_count` INT NOT NULL DEFAULT 0 COMMENT ''被引用次数'' AFTER `favorite_count`',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'quiz' AND column_name = 'use_count'
);
PREPARE quiz_use_count_stmt FROM @quiz_use_count_ddl;
EXECUTE quiz_use_count_stmt;
DEALLOCATE PREPARE quiz_use_count_stmt;

SET @quiz_shared_time_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `quiz` ADD COLUMN `shared_time` DATETIME DEFAULT NULL COMMENT ''设为公开的时间'' AFTER `use_count`',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'quiz' AND column_name = 'shared_time'
);
PREPARE quiz_shared_time_stmt FROM @quiz_shared_time_ddl;
EXECUTE quiz_shared_time_stmt;
DEALLOCATE PREPARE quiz_shared_time_stmt;

-- quiz_record 由「模板维度」改为「发布实例维度」，同一学生可在不同课程各答一次
SET @quiz_record_assignment_id_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `quiz_record` ADD COLUMN `assignment_id` BIGINT DEFAULT NULL COMMENT ''发布实例ID(为空表示旧数据)'' AFTER `quiz_id`',
              'DO 0')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'quiz_record' AND column_name = 'assignment_id'
);
PREPARE quiz_record_assignment_id_stmt FROM @quiz_record_assignment_id_ddl;
EXECUTE quiz_record_assignment_id_stmt;
DEALLOCATE PREPARE quiz_record_assignment_id_stmt;

SET @quiz_record_drop_uk_ddl = (
    SELECT IF(COUNT(*) > 0,
              'ALTER TABLE `quiz_record` DROP INDEX `uk_quiz_student`',
              'DO 0')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'quiz_record' AND index_name = 'uk_quiz_student'
);
PREPARE quiz_record_drop_uk_stmt FROM @quiz_record_drop_uk_ddl;
EXECUTE quiz_record_drop_uk_stmt;
DEALLOCATE PREPARE quiz_record_drop_uk_stmt;

SET @quiz_record_uk_assign_ddl = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE `quiz_record` ADD UNIQUE KEY `uk_assign_student` (`assignment_id`, `student_id`)',
              'DO 0')
    FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'quiz_record' AND index_name = 'uk_assign_student'
);
PREPARE quiz_record_uk_assign_stmt FROM @quiz_record_uk_assign_ddl;
EXECUTE quiz_record_uk_assign_stmt;
DEALLOCATE PREPARE quiz_record_uk_assign_stmt;

-- 存量数据迁移：旧课程测验生成对应的发布实例
INSERT INTO `quiz_assignment` (`quiz_id`, `course_id`, `teacher_id`, `title`, `duration_minutes`,
                               `start_time`, `end_time`, `status`, `deleted`, `create_time`, `update_time`)
SELECT q.`id`, q.`course_id`, q.`teacher_id`, q.`title`, q.`duration_minutes`,
       q.`start_time`, q.`end_time`,
       CASE WHEN q.`status` = 2 THEN 2
            WHEN q.`end_time` IS NOT NULL AND q.`end_time` < NOW() THEN 2
            WHEN q.`status` = 1 THEN 1
            ELSE 0 END,
       0, q.`create_time`, q.`update_time`
FROM `quiz` q
WHERE q.`course_id` IS NOT NULL
  AND q.`deleted` = 0
  AND NOT EXISTS (
      SELECT 1 FROM `quiz_assignment` a
      WHERE a.`quiz_id` = q.`id` AND a.`course_id` = q.`course_id`
  );

-- 存量答卷挂到对应的发布实例上
UPDATE `quiz_record` r
JOIN `quiz` q ON q.`id` = r.`quiz_id`
JOIN `quiz_assignment` a ON a.`quiz_id` = r.`quiz_id` AND a.`course_id` = q.`course_id`
SET r.`assignment_id` = a.`id`
WHERE r.`assignment_id` IS NULL;

-- 模板状态收敛为 0草稿 / 1已就绪，旧的「已结束」状态只保留在发布实例上
UPDATE `quiz` SET `status` = 1 WHERE `status` = 2;