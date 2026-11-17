package com.example.authengine.bootstrap;

import com.example.authengine.config.AuthProperties;
import com.example.authengine.domain.FactorType;
import com.example.authengine.domain.PolicyNode;
import com.example.authengine.entity.AppUser;
import com.example.authengine.mapper.AppUserMapper;
import com.example.authengine.policy.PolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认演示环境自动化数据初始化引导组件
 * <p>
 * 在开启 {@code app.auth.seed-demo-users: true} 时执行：
 * 1. 预置基础演示账号 (alice: 普通用户, admin: 策略管理员)；
 * 2. 初始预设默认双因子策略 (WEB_LOGIN: 密码/APP扫码二选一 + 短信动态码)；
 * 3. 生产环境通过配置禁用此引导。
 */
@Component
@RequiredArgsConstructor
public class DemoBootstrap implements CommandLineRunner {

    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final PolicyService policyService;
    private final AuthProperties authProperties;

    @Override
    public void run(String... args) {
        if (!authProperties.isSeedDemoUsers()) {
            return;
        }

        // 1. 初始化预设演示账号
        seedUser("alice", "Demo@123456", "USER", "13800000000");
        seedUser("admin", "Admin@123456", "ADMIN", "13900000000");

        // 2. 初始化默认策略：ALL(ANY(password, qr), sms)
        PolicyNode root = new PolicyNode(PolicyNode.Op.ALL, null, null, null, List.of(
                new PolicyNode(PolicyNode.Op.ANY, null, null, null, List.of(
                        factor("password", FactorType.PASSWORD),
                        factor("qr", FactorType.APP_QR_APPROVAL))),
                factor("sms", FactorType.SMS_OTP)
        ));

        policyService.ensureBootstrap(authProperties.getPolicyCode(), root);
    }

    private PolicyNode factor(String id, FactorType type) {
        return new PolicyNode(PolicyNode.Op.FACTOR, id, type, null, null);
    }

    /**
     * 若账号不存在则插入持久化存储
     */
    private void seedUser(String username, String rawPassword, String role, String mobile) {
        if (appUserMapper.findByUsername(username).isPresent()) {
            return;
        }

        AppUser user = AppUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .mobile(mobile)
                .enabled(true)
                .build();

        appUserMapper.insertSelective(user);
    }
}
