package com.example.authengine.bootstrap;

import com.example.authengine.config.AuthProperties;
import com.example.authengine.domain.FactorType;
import com.example.authengine.domain.PolicyNode;
import com.example.authengine.policy.PolicyService;
import com.example.authengine.user.AppUser;
import com.example.authengine.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认演示环境初始化：生产禁用 demo seed，不可使用这些固定账号。
 */
@Component
@RequiredArgsConstructor
public class DemoBootstrap implements CommandLineRunner {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final PolicyService policies;
    private final AuthProperties config;

    @Override
    public void run(String... args) {
        if (!config.isSeedDemoUsers()) return;
        seed("alice", "Demo@123456", "USER", "13800000000");
        seed("admin", "Admin@123456", "ADMIN", "13900000000");
        PolicyNode root = new PolicyNode(PolicyNode.Op.ALL, null, null, null, List.of(
                new PolicyNode(PolicyNode.Op.ANY, null, null, null, List.of(
                        factor("password", FactorType.PASSWORD),
                        factor("qr", FactorType.APP_QR_APPROVAL))),
                factor("sms", FactorType.SMS_OTP)
        ));
        policies.ensureBootstrap(config.getPolicyCode(), root);
    }

    private PolicyNode factor(String id, FactorType type) {
        return new PolicyNode(PolicyNode.Op.FACTOR, id, type, null, null);
    }

    private void seed(String username, String password, String role, String mobile) {
        if (users.findByUsername(username).isPresent()) return;
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setRole(role);
        user.setMobile(mobile);
        users.save(user);
    }
}
