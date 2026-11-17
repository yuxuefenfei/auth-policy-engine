package com.example.authengine.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 策略主表实体 (持久层对象)
 * <p>
 * 对应数据库表：{@code auth_policy}
 * 记录指定策略代码的当前生效版本指针与乐观锁版本，是多实例间共享策略状态的核心指针。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("auth_policy")
public class PolicyEntity {

    /**
     * 主键 ID (数据库自增)
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 策略代码 (如 WEB_LOGIN)
     */
    private String policyCode;

    /**
     * 当前已发布的生效版本号 (0 表示尚未发布任何版本)
     */
    @Builder.Default
    private int activeVersion = 0;

    /**
     * 最新生成的草稿或版本号
     */
    @Builder.Default
    private int latestVersion = 0;

    /**
     * 乐观锁版本号 (并发更新防冲突)
     */
    @Column(version = true)
    @Builder.Default
    private long lockVersion = 0L;

    /**
     * 便捷构造方法
     *
     * @param policyCode 策略唯一代码
     */
    public PolicyEntity(String policyCode) {
        this.policyCode = policyCode;
        this.activeVersion = 0;
        this.latestVersion = 0;
        this.lockVersion = 0L;
    }
}
