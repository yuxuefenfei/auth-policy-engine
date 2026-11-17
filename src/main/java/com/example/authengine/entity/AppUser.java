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
 * 应用用户实体 (持久层对象)
 * <p>
 * 对应数据库表：{@code app_user}
 * 存储系统内置及业务用户的认证密码哈希 (BCrypt)、关联手机号及 RBAC 角色代码。
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("app_user")
public class AppUser {

    /**
     * 主键 ID (数据库自增)
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 登录用户名 (全局唯一，如 alice, admin)
     */
    private String username;

    /**
     * BCrypt 散列后的口令密文
     */
    @Column("password_hash")
    private String passwordHash;

    /**
     * 绑定手机号码 (用于 SMS OTP 二次核验)
     */
    private String mobile;

    /**
     * 角色代码 (如 USER, ADMIN)
     */
    @Column("role_code")
    private String role;

    /**
     * 账号启用状态 (true: 正常, false: 禁用)
     */
    @Builder.Default
    private boolean enabled = true;
}
