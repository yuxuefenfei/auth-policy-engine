package com.example.authengine.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 策略版本快照实体 (持久层对象)
 * <p>
 * 对应数据库表：{@code auth_policy_version}
 * 存储不可变的策略 AST 定义 JSON 与 SHA256 防篡改完整性摘要。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("auth_policy_version")
public class PolicyVersionEntity {

    /**
     * 主键 ID (数据库自增)
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 所属策略代码 (如 WEB_LOGIN)
     */
    private String policyCode;

    /**
     * 策略版本号序列 (递增，如 1, 2, 3...)
     */
    private int versionNumber;

    /**
     * 策略 AST 语法树 JSON 序列化字符串
     */
    private String definition;

    /**
     * 策略定义的 SHA-256 完整性摘要
     */
    private String checksum;

    /**
     * 版本状态：DRAFT (草稿) / PUBLISHED (已发布)
     */
    private String status;

    /**
     * 创建时间戳
     */
    private Instant createdAt;

    /**
     * 发布生效时间戳 (未发布为 null)
     */
    private Instant publishedAt;
}
