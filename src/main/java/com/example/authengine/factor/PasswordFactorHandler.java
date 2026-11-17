package com.example.authengine.factor;

import com.example.authengine.attempt.FactorStep;
import com.example.authengine.attempt.LoginAttempt;
import com.example.authengine.domain.FactorType;
import com.example.authengine.mapper.AppUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 账号密码认证因子处理器 (PASSWORD)
 * <p>
 * 采用 BCrypt 安全散列算法进行密码单向比对校验，校验成功则推进该因子的达成状态。
 */
@Component
@RequiredArgsConstructor
public class PasswordFactorHandler implements FactorHandler {

    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public FactorType type() {
        return FactorType.PASSWORD;
    }

    /**
     * 核验密码凭证
     *
     * @param attempt    当前登录会话
     * @param step       当前因子节点步骤
     * @param credential 待校验的原始密码字符串
     * @return 密码匹配且用户有效返回 true，否则返回 false
     */
    @Override
    public boolean verify(LoginAttempt attempt, FactorStep step, String credential) {
        if (credential == null || credential.length() > 1024) {
            return false;
        }
        return appUserMapper.findByUsername(attempt.getUsername())
                .filter(u -> u.isEnabled() && u.getId().equals(attempt.getSubjectUserId()))
                .map(u -> passwordEncoder.matches(credential, u.getPasswordHash()))
                .orElse(false);
    }
}
