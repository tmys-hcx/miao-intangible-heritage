-- ============================================================================
-- 苗韵遗珍 · 全量建库脚本（最终形态：已整合 update.sql 全部升级内容）
-- ----------------------------------------------------------------------------
-- 内容：① 表结构（编号 1-29，建表定义即最终形态，已含 role_id / cover_url / sort_order 等全部增量字段）
--       ② 第 30 节「系统角色权限模块初始数据」：后台功能 / 系统角色 / 角色权限关系 / 初始管理员账号
-- 效果：执行本文件即得到最终数据库（结构与数据一次到位）；
--       update.sql 为存量库增量升级脚本（历史补丁集合），全新库无需执行
-- 说明：可重复执行——表为 DROP 重建；第 30 节配置数据为覆盖式写入，初始账号按账号判重不会重复插入
-- ============================================================================

CREATE DATABASE IF NOT EXISTS miao_intangible_heritage
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE miao_intangible_heritage;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. sys_user 用户表
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                            `account` varchar(64) NOT NULL COMMENT '账号',
                            `password` varchar(128) NOT NULL COMMENT '密码，加密存储',
                            `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
                            `avatar` varchar(512) DEFAULT NULL COMMENT '头像',
                            `identity_info` varchar(512) DEFAULT NULL COMMENT '身份信息',
                            `role` tinyint NOT NULL COMMENT '角色，0管理员,1非遗人,2普通用户',
                            `role_id` bigint DEFAULT NULL COMMENT '角色ID，关联sys_role.id（管理员绑定细分角色决定后台功能权限；非遗人/普通用户绑定各自角色）',
                            `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态，1启用,0禁用',
                            `phone` varchar(11) DEFAULT NULL COMMENT '手机号',
                            `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                            PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. intangible_culture 非遗文化资料表
DROP TABLE IF EXISTS `intangible_culture`;
CREATE TABLE `intangible_culture` (
                                      `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                      `publisher_id` bigint NOT NULL COMMENT '发布人ID',
                                      `title` varchar(256) NOT NULL COMMENT '标题',
                                      `content` text DEFAULT NULL COMMENT '内容',
                                      `img_url` varchar(2048) DEFAULT NULL COMMENT '图片地址，多张用JSON数组存储OSS地址',
                                      `video_url` varchar(512) DEFAULT NULL COMMENT '视频地址',
                                      `cover_url` varchar(1024) DEFAULT NULL COMMENT '视频封面缩略图OSS地址（无图片时发布端自动截帧上传）',
                                      `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态，0待审核，1已发布，2审核不通过',
                                      `view_count` bigint NOT NULL DEFAULT 0 COMMENT '浏览量',
                                      `sort_order` bigint NOT NULL DEFAULT 0 COMMENT '排版顺序，数值越大越靠前（0=未排版，按发布时间倒序排后）',
                                      `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                      PRIMARY KEY (`id`),
                                      KEY `idx_publisher_id` (`publisher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='非遗文化资料表';

-- 3. user_like_collect 点赞收藏表
DROP TABLE IF EXISTS `user_like_collect`;
CREATE TABLE `user_like_collect` (
                                     `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                     `user_id` bigint NOT NULL COMMENT '用户ID',
                                     `culture_id` bigint NOT NULL COMMENT '非遗资料ID',
                                     `type` tinyint NOT NULL COMMENT '类型，1点赞，2收藏',
                                     `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                     `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                     PRIMARY KEY (`id`),
                                     UNIQUE KEY `uk_user_culture_type` (`user_id`,`culture_id`,`type`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞收藏表';

-- 4. culture_comment 评论表
DROP TABLE IF EXISTS `culture_comment`;
CREATE TABLE `culture_comment` (
                                   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                   `culture_id` bigint NOT NULL COMMENT '非遗资料ID',
                                   `user_id` bigint NOT NULL COMMENT '用户ID',
                                   `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '所属顶级评论ID，0为顶级评论',
                                   `reply_user_id` bigint DEFAULT NULL COMMENT '被回复用户ID，二级回复定向标注，顶级评论为空',
                                   `comment_content` varchar(1024) NOT NULL COMMENT '评论内容',
                                   `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                   PRIMARY KEY (`id`),
                                   KEY `idx_culture_id` (`culture_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- 5. goods_category 商品分类表
DROP TABLE IF EXISTS `goods_category`;
CREATE TABLE `goods_category` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                  `category_name` varchar(128) NOT NULL COMMENT '分类名称',
                                  `category_desc` varchar(512) DEFAULT NULL COMMENT '分类描述',
                                  `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '父分类ID，0代表一级分类',
                                  `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
                                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                  PRIMARY KEY (`id`),
                                  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- 6. intangible_goods 非遗商品表
DROP TABLE IF EXISTS `intangible_goods`;
CREATE TABLE `intangible_goods` (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                    `publisher_id` bigint NOT NULL COMMENT '发布人ID',
                                    `category_id` bigint NOT NULL COMMENT '分类ID',
                                    `goods_name` varchar(256) NOT NULL COMMENT '商品名称',
                                    `goods_desc` text DEFAULT NULL COMMENT '商品描述',
                                    `goods_img` varchar(2048) DEFAULT NULL COMMENT '商品图片，JSON数组存储OSS地址',
                                    `price` decimal(10,2) NOT NULL COMMENT '价格',
                                    `stock` int NOT NULL DEFAULT 0 COMMENT '库存',
                                    `delivery_time` int DEFAULT NULL COMMENT '发货时间，单位天',
                                    `limit_buy_num` int DEFAULT NULL COMMENT '限购数量',
                                    `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态，0待审核 1已上架 2已打回 3已下架',
                                    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                    PRIMARY KEY (`id`),
                                    KEY `idx_publisher_id` (`publisher_id`),
                                    KEY `idx_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='非遗商品表';

DROP TABLE IF EXISTS `goods_order_item`;
DROP TABLE IF EXISTS `goods_order`;

-- 7. goods_order 订单主表
CREATE TABLE `goods_order` (
                               `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                               `order_no` varchar(64) NOT NULL COMMENT '订单编号，唯一，系统生成',
                               `batch_no` varchar(64) DEFAULT NULL COMMENT '结算批次号',
                               `user_id` bigint NOT NULL COMMENT '下单用户ID',
                               `address_id` bigint NOT NULL COMMENT '收货地址ID，关联user_address.id',
                               `receiver_name` varchar(64) NOT NULL COMMENT '收货人姓名，下单时地址快照',
                               `receiver_phone` varchar(11) NOT NULL COMMENT '收货人手机号，下单时地址快照',
                               `receiver_address` varchar(512) NOT NULL COMMENT '收货完整地址',
                               `order_remark` varchar(512) DEFAULT NULL COMMENT '订单备注',
                               `total_price` decimal(10,2) NOT NULL COMMENT '商品合计金额',
                               `order_status` tinyint NOT NULL COMMENT '订单状态，0待付款 1待发货 2已发货 3已收货 4已退款 5已取消 6已评价 7已完成 8退款申请中',
                               `refund_reason` varchar(512) DEFAULT NULL COMMENT '退款申请原因',
                               `refund_apply_time` datetime DEFAULT NULL COMMENT '退款申请时间',
                               `refund_prev_status` tinyint DEFAULT NULL COMMENT '退款申请时的订单状态',
                               `refund_reject_reason` varchar(512) DEFAULT NULL COMMENT '退款驳回原因',
                               `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_order_no` (`order_no`),
                               KEY `idx_user_id` (`user_id`),
                               KEY `idx_batch_no` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 8. goods_order_item 订单商品明细表
CREATE TABLE `goods_order_item` (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                    `order_id` bigint NOT NULL COMMENT '订单ID，关联goods_order.id',
                                    `order_no` varchar(64) NOT NULL COMMENT '订单编号',
                                    `goods_id` bigint NOT NULL COMMENT '商品ID',
                                    `publisher_id` bigint NOT NULL COMMENT '卖家非遗人ID',
                                    `goods_name` varchar(256) NOT NULL COMMENT '商品名称，下单快照',
                                    `goods_img` varchar(2048) DEFAULT NULL COMMENT '商品图片',
                                    `price` decimal(10,2) NOT NULL COMMENT '商品单价，下单快照',
                                    `goods_num` int NOT NULL COMMENT '购买数量',
                                    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                    PRIMARY KEY (`id`),
                                    KEY `idx_order_id` (`order_id`),
                                    KEY `idx_publisher_id` (`publisher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单商品明细表';

SET FOREIGN_KEY_CHECKS = 1;

-- 9. goods_evaluate 商品评价表
DROP TABLE IF EXISTS `goods_evaluate`;
CREATE TABLE `goods_evaluate` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                  `order_id` bigint NOT NULL COMMENT '订单ID',
                                  `goods_id` bigint NOT NULL COMMENT '商品ID',
                                  `user_id` bigint NOT NULL COMMENT '用户ID',
                                  `evaluate_content` varchar(1024) DEFAULT NULL COMMENT '评价内容',
                                  `score` tinyint NOT NULL COMMENT '评分',
                                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                  PRIMARY KEY (`id`),
                                  KEY `idx_goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价表';

-- 10. goods_evaluate_reply 商品评价追问回复表
DROP TABLE IF EXISTS `goods_evaluate_reply`;
CREATE TABLE `goods_evaluate_reply` (
                                        `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                        `evaluate_id` bigint NOT NULL COMMENT '评价ID，关联goods_evaluate.id',
                                        `user_id` bigint NOT NULL COMMENT '回复用户ID',
                                        `parent_reply_id` bigint DEFAULT NULL COMMENT '父级追问ID',
                                        `reply_to_user_id` bigint DEFAULT NULL COMMENT '被回复用户ID',
                                        `reply_content` varchar(1024) NOT NULL COMMENT '追问回复内容',
                                        `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                        `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                        `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                        PRIMARY KEY (`id`),
                                        KEY `idx_evaluate_id` (`evaluate_id`),
                                        KEY `idx_parent_reply_id` (`parent_reply_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价追问回复表';

-- 11. intangible_video 学习视频表（非遗人发布 + 管理员审核；前台仅展示已通过视频）
DROP TABLE IF EXISTS `intangible_video`;
CREATE TABLE `intangible_video` (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                    `publisher_id` bigint NOT NULL COMMENT '发布人ID（非遗人，关联sys_user.id）',
                                    `title` varchar(256) NOT NULL COMMENT '视频标题',
                                    `content` text DEFAULT NULL COMMENT '视频简介',
                                    `img_url` varchar(1024) DEFAULT NULL COMMENT '封面缩略图OSS地址（发布时自动截帧上传）',
                                    `video_url` varchar(1024) DEFAULT NULL COMMENT '视频OSS地址',
                                    `duration` varchar(16) DEFAULT NULL COMMENT '视频时长（如12:30）',
                                    `audit_status` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态：0待审核，1已通过，2已驳回',
                                    `audit_remark` varchar(512) DEFAULT NULL COMMENT '审核备注/驳回原因',
                                    `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
                                    `auditor_id` bigint DEFAULT NULL COMMENT '审核管理员ID，关联sys_user.id',
                                    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
                                    PRIMARY KEY (`id`),
                                    KEY `idx_publisher_id` (`publisher_id`),
                                    KEY `idx_audit_status` (`audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习视频表（传承预约-视频学习板块：非遗人发布+管理员审核）';

-- 12. intangible_interview 访谈资料表（管理员维护，无审核环节；正文 HTML 富文本图文穿插）
DROP TABLE IF EXISTS `intangible_interview`;
CREATE TABLE `intangible_interview` (
                                        `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                        `publisher_id` bigint NOT NULL COMMENT '发布人ID（管理员，关联sys_user.id）',
                                        `title` varchar(256) NOT NULL COMMENT '标题',
                                        `content` text DEFAULT NULL COMMENT '正文（HTML富文本，图文穿插）',
                                        `img_url` varchar(1024) DEFAULT NULL COMMENT '封面/配图OSS地址，多图英文逗号隔开',
                                        `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                        `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                        `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
                                        PRIMARY KEY (`id`),
                                        KEY `idx_publisher_id` (`publisher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访谈资料表（传承预约-访谈资料板块：口述实录，图文穿插）';

-- 13. intangible_knowledge 知识窗口表（管理员维护，无审核环节；正文 HTML 富文本图文穿插）
DROP TABLE IF EXISTS `intangible_knowledge`;
CREATE TABLE `intangible_knowledge` (
                                        `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                        `publisher_id` bigint NOT NULL COMMENT '发布人ID（管理员，关联sys_user.id）',
                                        `title` varchar(256) NOT NULL COMMENT '标题',
                                        `content` text DEFAULT NULL COMMENT '正文（HTML富文本，图文穿插）',
                                        `img_url` varchar(1024) DEFAULT NULL COMMENT '封面/配图OSS地址，多图英文逗号隔开',
                                        `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                        `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                        `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
                                        PRIMARY KEY (`id`),
                                        KEY `idx_publisher_id` (`publisher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识窗口表（传承预约-知识窗口板块：文化常识，图文穿插）';

-- 14. user_watch_record 学习视频观看记录表（完整观看后写入，重复观看刷新最近观看时间）
DROP TABLE IF EXISTS `user_watch_record`;
CREATE TABLE `user_watch_record` (
                                     `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                     `user_id` bigint NOT NULL COMMENT '用户ID，关联sys_user.id（非遗人 / 普通用户）',
                                     `video_id` bigint NOT NULL COMMENT '学习视频ID，关联intangible_video.id',
                                     `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间（最近完整观看时间）',
                                     `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
                                     PRIMARY KEY (`id`),
                                     UNIQUE KEY `uk_user_video` (`user_id`, `video_id`),
                                     KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习视频观看记录表（完整观看后写入，重复观看刷新最近观看时间）';

-- 15. intangible_news 非遗资讯公告表
DROP TABLE IF EXISTS `intangible_news`;
CREATE TABLE `intangible_news` (
                                   `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                   `admin_id` bigint NOT NULL COMMENT '管理员ID',
                                   `title` varchar(256) NOT NULL COMMENT '标题',
                                   `content` text DEFAULT NULL COMMENT '内容',
                                   `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                   PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='非遗资讯公告表';

-- 16. chat_message 聊天消息表
DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message` (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                `send_user_id` bigint NOT NULL COMMENT '发送用户ID',
                                `receive_user_id` bigint NOT NULL COMMENT '接收用户ID',
                                `msg_content` varchar(1024) NOT NULL COMMENT '消息内容',
                                `is_read` tinyint NOT NULL DEFAULT 0 COMMENT '是否已读，0未读 1已读',
                                `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                PRIMARY KEY (`id`),
                                KEY `idx_send_receive` (`send_user_id`,`receive_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息表';

-- 17. sys_banner 系统轮播图表
DROP TABLE IF EXISTS `sys_banner`;
CREATE TABLE `sys_banner` (
                              `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                              `banner_img` varchar(512) NOT NULL COMMENT '轮播媒体地址，图片视频OSS URL',
                              `banner_type` tinyint NOT NULL DEFAULT 1 COMMENT '轮播类型，1图片 2视频',
                              `banner_desc` varchar(256) DEFAULT NULL COMMENT '轮播描述',
                              `sort` int NOT NULL DEFAULT 0 COMMENT '排序',
                              `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                              PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统轮播图表';

-- 18. sys_log 系统日志表
DROP TABLE IF EXISTS `sys_log`;
CREATE TABLE `sys_log` (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                           `operate_user_id` bigint DEFAULT NULL COMMENT '操作用户ID',
                           `operate_module` varchar(128) DEFAULT NULL COMMENT '操作模块',
                           `operate_content` varchar(512) DEFAULT NULL COMMENT '操作内容',
                           `ip` varchar(64) DEFAULT NULL COMMENT 'IP地址',
                           `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                           PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统日志表';

-- 19. user_address 用户收货地址表
DROP TABLE IF EXISTS `user_address`;
CREATE TABLE `user_address` (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                `user_id` bigint NOT NULL COMMENT '所属用户ID，关联sys_user.id',
                                `receiver_name` varchar(64) NOT NULL COMMENT '收货人姓名',
                                `receiver_phone` varchar(11) NOT NULL COMMENT '收货人手机号',
                                `province` varchar(32) NOT NULL COMMENT '省份名称',
                                `city` varchar(32) NOT NULL COMMENT '城市名称',
                                `district` varchar(32) NOT NULL COMMENT '区/县名称',
                                `detail_address` varchar(256) NOT NULL COMMENT '详细街道门牌号地址',
                                `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否默认地址，0否，1是',
                                `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                PRIMARY KEY (`id`),
                                KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收货地址表';

-- 20. heritage_apply 非遗人申请表
DROP TABLE IF EXISTS `heritage_apply`;
CREATE TABLE `heritage_apply` (
                                  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                  `user_id` bigint NOT NULL COMMENT '申请用户ID，关联sys_user.id',
                                  `identity_info` varchar(1024) NOT NULL COMMENT '身份信息，传承经历、师承关系',
                                  `apply_reason` varchar(1024) NOT NULL COMMENT '申请原因',
                                  `material_urls` varchar(2048) NOT NULL COMMENT '证明材料图片地址，JSON数组字符串',
                                  `audit_status` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态，0待审核，1已通过，2已驳回',
                                  `audit_remark` varchar(512) DEFAULT NULL COMMENT '审核备注驳回原因',
                                  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
                                  `auditor_id` bigint DEFAULT NULL COMMENT '审核管理员ID，关联sys_user.id',
                                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                  PRIMARY KEY (`id`),
                                  KEY `idx_user_id` (`user_id`),
                                  KEY `idx_audit_status` (`audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='非遗人申请表';

-- 21. inherit_forum_post 论坛帖子表
DROP TABLE IF EXISTS `inherit_forum_post`;
CREATE TABLE `inherit_forum_post` (
                                      `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                      `publisher_id` bigint NOT NULL COMMENT '发帖人ID，关联sys_user.id',
                                      `title` varchar(256) NOT NULL COMMENT '帖子标题',
                                      `content` text DEFAULT NULL COMMENT '帖子内容',
                                      `topic_id` bigint DEFAULT NULL COMMENT '话题ID，关联inherit_forum_topic.id，NULL为无话题',
                                      `img_url` varchar(4096) DEFAULT NULL COMMENT '配图OSS地址，多图JSON数组字符串',
                                      `video_url` varchar(1024) DEFAULT NULL COMMENT '视频OSS地址',
                                      `audit_status` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态，0待审核，1已通过，2已驳回',
                                      `audit_remark` varchar(512) DEFAULT NULL COMMENT '审核备注驳回原因',
                                      `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
                                      `auditor_id` bigint DEFAULT NULL COMMENT '审核管理员ID，关联sys_user.id',
                                      `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                      PRIMARY KEY (`id`),
                                      KEY `idx_publisher_id` (`publisher_id`),
                                      KEY `idx_topic_id` (`topic_id`),
                                      KEY `idx_audit_status` (`audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛帖子表';

-- 22. inherit_forum_topic 论坛话题表
DROP TABLE IF EXISTS `inherit_forum_topic`;
CREATE TABLE `inherit_forum_topic` (
                                       `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                       `name` varchar(64) NOT NULL COMMENT '话题名',
                                       `use_count` int NOT NULL DEFAULT 0 COMMENT '使用次数',
                                       `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                       `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                       `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                       PRIMARY KEY (`id`),
                                       UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛话题表';

-- 23. inherit_forum_reply 论坛回复表
DROP TABLE IF EXISTS `inherit_forum_reply`;
CREATE TABLE `inherit_forum_reply` (
                                       `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                       `post_id` bigint NOT NULL COMMENT '帖子ID，关联inherit_forum_post.id',
                                       `user_id` bigint NOT NULL COMMENT '回复用户ID，关联sys_user.id',
                                       `parent_id` bigint NOT NULL DEFAULT 0 COMMENT '所属顶级回复ID，0为顶级回复',
                                       `reply_user_id` bigint DEFAULT NULL COMMENT '被回复用户ID',
                                       `reply_content` varchar(1024) NOT NULL COMMENT '回复内容',
                                       `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                       `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                       `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                       PRIMARY KEY (`id`),
                                       KEY `idx_post_id` (`post_id`),
                                       KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛回复表';

-- 24. inherit_forum_like 论坛帖子点赞表
DROP TABLE IF EXISTS `inherit_forum_like`;
CREATE TABLE `inherit_forum_like` (
                                      `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                      `user_id` bigint NOT NULL COMMENT '点赞用户ID，关联sys_user.id',
                                      `post_id` bigint NOT NULL COMMENT '帖子ID，关联inherit_forum_post.id',
                                      `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0有效点赞 1已取消',
                                      PRIMARY KEY (`id`),
                                      UNIQUE KEY `uk_user_post` (`user_id`,`post_id`,`is_deleted`),
                                      KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛帖子点赞表';

-- 25. inherit_forum_reply_like 论坛评论点赞表
DROP TABLE IF EXISTS `inherit_forum_reply_like`;
CREATE TABLE `inherit_forum_reply_like` (
                                            `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                            `user_id` bigint NOT NULL COMMENT '点赞用户ID，关联sys_user.id',
                                            `reply_id` bigint NOT NULL COMMENT '评论回复ID，关联inherit_forum_reply.id',
                                            `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                            `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                            `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0有效点赞 1已取消',
                                            PRIMARY KEY (`id`),
                                            UNIQUE KEY `uk_user_reply` (`user_id`,`reply_id`,`is_deleted`),
                                            KEY `idx_reply_id` (`reply_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论坛评论点赞表';

-- 26. notification 消息通知表（评论 / 回复 / 购物等业务事件通知，由通知服务统一写入与维护）
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
                                `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                `receive_user_id` bigint NOT NULL COMMENT '接收用户ID，关联sys_user.id',
                                `send_user_id` bigint DEFAULT NULL COMMENT '触发用户ID，关联sys_user.id，系统通知为空',
                                `notify_type` tinyint NOT NULL COMMENT '通知类型，1文章评论 2文章回复 3论坛帖子回复 4论坛评论回复 5购物订单 6退款通知 7系统通知',
                                `title` varchar(128) NOT NULL COMMENT '通知标题',
                                `content` varchar(512) DEFAULT NULL COMMENT '通知内容摘要',
                                `biz_type` varchar(32) DEFAULT NULL COMMENT '业务类型，culture非遗文章 forum论坛帖子 order商品订单',
                                `biz_id` varchar(64) DEFAULT NULL COMMENT '业务对象ID，文章ID / 帖子ID / 订单编号，点击跳转用',
                                `is_read` tinyint NOT NULL DEFAULT 0 COMMENT '是否已读，0未读 1已读',
                                `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                PRIMARY KEY (`id`),
                                KEY `idx_receive_read` (`receive_user_id`,`is_read`),
                                KEY `idx_receive_time` (`receive_user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息通知表';

-- 27. sys_admin_function 后台管理功能表（管理端菜单 / 权限点：一行一个后台功能）
DROP TABLE IF EXISTS `sys_admin_function`;
CREATE TABLE `sys_admin_function` (
                                      `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                      `function_name` varchar(64) NOT NULL COMMENT '功能名称（管理端菜单名）',
                                      `function_code` varchar(64) NOT NULL COMMENT '功能编码，唯一权限标识（如 goods:audit）',
                                      `route_path` varchar(128) NOT NULL COMMENT '前端路由路径（管理端菜单跳转地址，如 /admin/goods-audit）',
                                      `sort` int NOT NULL DEFAULT 0 COMMENT '排序（管理端菜单展示顺序）',
                                      `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态，1启用 0停用',
                                      `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                      PRIMARY KEY (`id`),
                                      UNIQUE KEY `uk_function_code` (`function_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台管理功能表（管理员角色可分配的后台功能/菜单）';

-- 28. sys_role 系统角色表（统一角色体系：管理员细分角色 + 非遗传承人 + 普通用户）
--     user_role 与 sys_user.role 对应（0管理员 1非遗人 2普通用户）；user_role=0 的角色经 sys_user.role_id 绑定决定后台功能权限
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
                           `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                           `role_name` varchar(64) NOT NULL COMMENT '角色名称',
                           `role_code` varchar(64) NOT NULL COMMENT '角色编码，唯一标识（SUPER_ADMIN / MALL_ADMIN / CONTENT_ADMIN / SYS_ADMIN / INHERITOR / USER）',
                           `user_role` tinyint NOT NULL COMMENT '角色类别，与sys_user.role对应：0管理员 1非遗人 2普通用户',
                           `role_desc` varchar(256) DEFAULT NULL COMMENT '角色描述',
                           `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态，1启用 0停用',
                           `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                           PRIMARY KEY (`id`),
                           UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表（统一角色体系：管理员细分角色 / 非遗传承人 / 普通用户）';

-- 29. sys_role_function 角色功能关系表（角色 × 后台功能；仅管理员类角色分配后台功能）
DROP TABLE IF EXISTS `sys_role_function`;
CREATE TABLE `sys_role_function` (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                    `role_id` bigint NOT NULL COMMENT '角色ID，关联sys_role.id',
                                    `function_id` bigint NOT NULL COMMENT '后台功能ID，关联sys_admin_function.id',
                                    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除，0未删除 1已删除',
                                    PRIMARY KEY (`id`),
                                    UNIQUE KEY `uk_role_function` (`role_id`,`function_id`,`is_deleted`),
                                    KEY `idx_function_id` (`function_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色功能关系表（角色拥有的后台功能）';

-- 30. 系统角色权限模块初始数据（覆盖式写入：先清空三张表再重插，可重复执行不报重复键）
--     说明：与 update.sql 第 5-9 节数据一致；功能固定 ID 1-12、角色固定 ID 1-6，保证 sys_user.role_id 引用稳定
--     内容：30.1 后台功能 / 30.2 系统角色 / 30.3 角色权限关系 / 30.4 初始管理员账号 / 30.5 绑定兜底 / 30.6 验证
--     注意：重跑本段会把功能 / 角色 / 权限配置重置为初始状态（界面上的调整会丢失）；初始账号按账号判重不受影响

-- 30.0 清空旧数据并重置自增（先清关系表，再清主数据表；保证下方固定 ID 顺利写入）
DELETE FROM `sys_role_function`;
DELETE FROM `sys_role`;
DELETE FROM `sys_admin_function`;
ALTER TABLE `sys_role_function` AUTO_INCREMENT = 1;
ALTER TABLE `sys_role` AUTO_INCREMENT = 1;
ALTER TABLE `sys_admin_function` AUTO_INCREMENT = 1;

-- 30.1 后台管理功能（固定 ID 1-12，管理端菜单登记，sort 为菜单展示顺序；含「角色管理」「非遗文化排版」）
INSERT INTO `sys_admin_function` (`id`, `function_name`, `function_code`, `route_path`, `sort`) VALUES
    (1, '商品分类', 'category:manage', '/admin/category', 1),
    (2, '商品审核', 'goods:audit', '/admin/goods-audit', 2),
    (3, '订单管理', 'order:manage', '/admin/order', 3),
    (4, '非遗文化文章管理', 'culture:audit', '/admin/culture-audit', 4),
    (5, '论坛审核', 'forum:audit', '/admin/forum-audit', 5),
    (6, '传承信息管理', 'inherit:manage', '/admin/inherit', 6),
    (7, '申请审核', 'apply:audit', '/admin/apply-audit', 7),
    (8, '用户管理', 'user:manage', '/admin/user', 8),
    (9, '轮播图管理', 'banner:manage', '/admin/banner', 9),
    (10, '系统日志', 'log:view', '/admin/log', 10),
    (11, '角色管理', 'role:manage', '/admin/role', 11),
    (12, '非遗文化排版', 'culture:layout', '/admin/culture-layout', 12);

-- 30.2 系统角色（固定 ID 1-6：1-4 管理员细分角色，5 非遗传承人，6 普通用户）
INSERT INTO `sys_role` (`id`, `role_name`, `role_code`, `user_role`, `role_desc`) VALUES
    (1, '超级管理员', 'SUPER_ADMIN', 0, '拥有后台全部功能权限（系统兜底，存量管理员账号默认绑定）'),
    (2, '商城管理员', 'MALL_ADMIN', 0, '负责商品分类 / 商品审核 / 订单管理'),
    (3, '内容管理员', 'CONTENT_ADMIN', 0, '负责论坛审核 / 传承信息管理 / 非遗文化文章管理'),
    (4, '系统管理员', 'SYS_ADMIN', 0, '负责申请审核 / 用户管理 / 轮播图管理 / 系统日志 / 角色管理'),
    (5, '非遗传承人', 'INHERITOR', 1, '经审核认证的非遗传承人，可发布商品 / 学习视频并在论坛发帖'),
    (6, '普通用户', 'USER', 2, '普通注册用户，可浏览非遗资料 / 学习视频并参与论坛互动');

-- 30.3 角色功能权限关系（超级管理员：动态绑定全部功能——按功能表现量绑定，后续新增功能自动覆盖，
--      保证「初始超级管理员具备后台所有功能」；商城 / 内容 / 系统管理员：按业务分工）
INSERT INTO `sys_role_function` (`role_id`, `function_id`)
SELECT r.id, f.id FROM `sys_role` r, `sys_admin_function` f
WHERE r.role_code = 'SUPER_ADMIN';

INSERT INTO `sys_role_function` (`role_id`, `function_id`)
SELECT r.id, f.id FROM `sys_role` r, `sys_admin_function` f
WHERE r.role_code = 'MALL_ADMIN'
  AND f.function_code IN ('category:manage', 'goods:audit', 'order:manage');

INSERT INTO `sys_role_function` (`role_id`, `function_id`)
SELECT r.id, f.id FROM `sys_role` r, `sys_admin_function` f
WHERE r.role_code = 'CONTENT_ADMIN'
  AND f.function_code IN ('forum:audit', 'inherit:manage', 'culture:audit');

INSERT INTO `sys_role_function` (`role_id`, `function_id`)
SELECT r.id, f.id FROM `sys_role` r, `sys_admin_function` f
WHERE r.role_code = 'SYS_ADMIN'
  AND f.function_code IN ('apply:audit', 'user:manage', 'banner:manage', 'log:view', 'role:manage');

-- 30.4 初始管理员账号（超级管理员 + 3 个细分管理员演示账号，密码均为 123456，按账号判重，重复执行不会重复插入；
--      password 为 MD5 加密存储（e10adc... = 123456），与登录校验方式一致；
--      admin_super 即初始超级管理员：绑定 SUPER_ADMIN 角色 → 经 30.3 动态授权具备后台所有功能）
INSERT INTO `sys_user` (`account`, `password`, `nickname`, `role`, `role_id`, `status`)
SELECT 'admin_super', 'e10adc3949ba59abbe56e057f20f883e', '超级管理员', 0,
       (SELECT `id` FROM `sys_role` WHERE `role_code` = 'SUPER_ADMIN' LIMIT 1), 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `account` = 'admin_super' AND `is_deleted` = 0);

INSERT INTO `sys_user` (`account`, `password`, `nickname`, `role`, `role_id`, `status`)
SELECT 'admin_mall', 'e10adc3949ba59abbe56e057f20f883e', '商城管理员', 0,
       (SELECT `id` FROM `sys_role` WHERE `role_code` = 'MALL_ADMIN' LIMIT 1), 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `account` = 'admin_mall' AND `is_deleted` = 0);

INSERT INTO `sys_user` (`account`, `password`, `nickname`, `role`, `role_id`, `status`)
SELECT 'admin_content', 'e10adc3949ba59abbe56e057f20f883e', '内容管理员', 0,
       (SELECT `id` FROM `sys_role` WHERE `role_code` = 'CONTENT_ADMIN' LIMIT 1), 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `account` = 'admin_content' AND `is_deleted` = 0);

INSERT INTO `sys_user` (`account`, `password`, `nickname`, `role`, `role_id`, `status`)
SELECT 'admin_sys', 'e10adc3949ba59abbe56e057f20f883e', '系统管理员', 0,
       (SELECT `id` FROM `sys_role` WHERE `role_code` = 'SYS_ADMIN' LIMIT 1), 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `account` = 'admin_sys' AND `is_deleted` = 0);

-- 30.5 角色绑定兜底修正（防止 role_id 指向被清理的旧角色：按身份大类绑回对应默认角色）
UPDATE `sys_user`
SET `role_id` = (SELECT `id` FROM `sys_role` WHERE `role_code` = 'SUPER_ADMIN' LIMIT 1)
WHERE `role` = 0
  AND (`role_id` IS NULL
       OR `role_id` NOT IN (SELECT `id` FROM `sys_role` WHERE `is_deleted` = 0));

UPDATE `sys_user`
SET `role_id` = (SELECT `id` FROM `sys_role` WHERE `role_code` = 'INHERITOR' LIMIT 1)
WHERE `role` = 1
  AND (`role_id` IS NULL
       OR `role_id` NOT IN (SELECT `id` FROM `sys_role` WHERE `is_deleted` = 0));

UPDATE `sys_user`
SET `role_id` = (SELECT `id` FROM `sys_role` WHERE `role_code` = 'USER' LIMIT 1)
WHERE `role` = 2
  AND (`role_id` IS NULL
       OR `role_id` NOT IN (SELECT `id` FROM `sys_role` WHERE `is_deleted` = 0));

-- 30.6 执行后验证（预期：功能总数 12；超管 12 项；商城 3 / 内容 3 / 系统 5；管理员账号 4 个）
SELECT COUNT(*) AS function_total FROM `sys_admin_function` WHERE `is_deleted` = 0;

SELECT r.`role_code`, COUNT(*) AS function_count
FROM `sys_role_function` rf
JOIN `sys_role` r ON r.id = rf.`role_id`
WHERE rf.`is_deleted` = 0
GROUP BY r.id, r.`role_code`
ORDER BY r.`role_code`;

SELECT `id`, `account`, `nickname`, `role`, `role_id`
FROM `sys_user`
WHERE `role` = 0 AND `is_deleted` = 0;

-- 31. TCC 分布式事务：下单 Redis 预扣库存（Seata 2.5.0 TCC 模式）
-- 31.1 goods_stock_freeze 库存冻结流水表：Try 插入 T 记录，Confirm 置 C（DB 落库扣减），Cancel 置 R（Redis 还原）；
--      唯一键 (order_no, goods_id) 保证二阶段重复投递时业务幂等；未终结（T）冻结参与启动预热时的可用库存回算
DROP TABLE IF EXISTS `goods_stock_freeze`;
CREATE TABLE `goods_stock_freeze` (
                                    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                    `xid` varchar(128) NOT NULL COMMENT 'Seata全局事务ID',
                                    `order_no` varchar(64) NOT NULL COMMENT '订单编号（库存预留凭据）',
                                    `goods_id` bigint NOT NULL COMMENT '商品ID',
                                    `num` int NOT NULL COMMENT '预留（冻结）数量',
                                    `status` char(1) NOT NULL DEFAULT 'T' COMMENT '冻结状态，T已预留 C已提交(待释放) F已释放(终态) R已取消',
                                    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    PRIMARY KEY (`id`),
                                    UNIQUE KEY `uk_order_goods` (`order_no`, `goods_id`),
                                    KEY `idx_goods_id` (`goods_id`),
                                    KEY `idx_xid` (`xid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存冻结流水表（TCC预扣库存：Try冻结/Confirm落库/Cancel还原）';

-- 31.2 tcc_fence_log Seata 2.5.0 TCC Fence 日志表：表名由框架默认写死仍叫 tcc_fence_log（实测报
--      Table 'tcc_fence_log' doesn't exist），但 2.5 的 CommonFenceStoreDataBaseDAO 列结构已改为新版：
--      自增主键 id + 唯一键(xid, branch_id) + gmt_create/gmt_modified + description，
--      旧版 creation_date/expiration_date 列结构会报 Unknown column 'gmt_create'，勿用）；
--      空回滚 / 悬挂 / 幂等三防由 seata.tcc.fence.enable=true 自动读写；
--      实测 seata-all 2.5.0 的 fence INSERT 不写 action_id（严格模式下 NOT NULL 无默认值会报
--      Field 'action_id' doesn't have a default value），故除表键列外均放宽为可空/带默认值
DROP TABLE IF EXISTS `tcc_fence_log`;
CREATE TABLE `tcc_fence_log` (
                              `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                              `xid` varchar(100) NOT NULL COMMENT '全局事务ID',
                              `branch_id` bigint NOT NULL COMMENT '分支事务ID',
                              `action_id` bigint NULL DEFAULT NULL COMMENT 'TCC Action ID（2.5.0 框架不写入，可空）',
                              `action_name` varchar(64) NULL DEFAULT NULL COMMENT 'TCC Action 名称',
                              `description` varchar(255) DEFAULT NULL COMMENT '描述',
                              `status` tinyint NULL DEFAULT NULL COMMENT 'Fence状态',
                              `gmt_create` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `gmt_modified` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_xid_branch_id` (`xid`, `branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Seata TCC Fence日志表（空回滚/悬挂/幂等三防，框架自动维护）';


