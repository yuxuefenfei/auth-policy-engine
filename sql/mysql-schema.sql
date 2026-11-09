-- 生产初始化参考：先审查/版本化数据库迁移，再启用 spring.jpa.hibernate.ddl-auto=validate。
CREATE TABLE IF NOT EXISTS app_user
(
    id
    BIGINT
    NOT
    NULL
    AUTO_INCREMENT
    PRIMARY
    KEY,
    username
    VARCHAR
(
    100
) NOT NULL UNIQUE,
    password_hash VARCHAR
(
    100
) NOT NULL,
    mobile VARCHAR
(
    30
) NULL,
    role_code VARCHAR
(
    32
) NOT NULL,
    enabled BIT NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS auth_policy
(
    id
    BIGINT
    NOT
    NULL
    AUTO_INCREMENT
    PRIMARY
    KEY,
    policy_code
    VARCHAR
(
    80
) NOT NULL UNIQUE,
    active_version INT NOT NULL DEFAULT 0,
    latest_version INT NOT NULL DEFAULT 0,
    lock_version BIGINT NOT NULL DEFAULT 0
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS auth_policy_version
(
    id
    BIGINT
    NOT
    NULL
    AUTO_INCREMENT
    PRIMARY
    KEY,
    policy_code
    VARCHAR
(
    80
) NOT NULL,
    version_number INT NOT NULL,
    definition LONGTEXT NOT NULL,
    checksum VARCHAR
(
    64
) NOT NULL,
    status VARCHAR
(
    24
) NOT NULL,
    created_at DATETIME
(
    6
) NULL,
    published_at DATETIME
(
    6
) NULL,
    UNIQUE KEY uk_auth_policy_version
(
    policy_code,
    version_number
)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
