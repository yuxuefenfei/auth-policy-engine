-- ===================================================================
-- 认证策略引擎表结构定义 (兼容 H2 MySQL 模式与生产 MySQL)
-- ===================================================================

-- 1. 用户基础表
CREATE TABLE IF NOT EXISTS app_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    mobile VARCHAR(30),
    role_code VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_app_user_username UNIQUE (username)
);

-- 2. 策略主表 (记录当前生效版本与乐观锁)
CREATE TABLE IF NOT EXISTS auth_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_code VARCHAR(80) NOT NULL,
    active_version INT NOT NULL DEFAULT 0,
    latest_version INT NOT NULL DEFAULT 0,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_auth_policy_code UNIQUE (policy_code)
);

-- 3. 策略版本历史表 (快照不可变存储)
CREATE TABLE IF NOT EXISTS auth_policy_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_code VARCHAR(80) NOT NULL,
    version_number INT NOT NULL,
    definition LONGTEXT NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP NULL,
    published_at TIMESTAMP NULL,
    CONSTRAINT uk_auth_policy_version UNIQUE (policy_code, version_number)
);
