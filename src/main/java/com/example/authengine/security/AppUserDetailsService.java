package com.example.authengine.security;

import com.example.authengine.entity.AppUser;
import com.example.authengine.mapper.AppUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 用户认证详情加载服务
 * <p>
 * 基于 MyBatis-Flex {@link AppUserMapper} 加载系统账号、口令哈希及权限角色。
 */
@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserMapper appUserMapper;

    /**
     * 根据用户名加载用户认证主体
     *
     * @param username 登录用户名
     * @return Spring Security UserDetails 实例
     * @throws UsernameNotFoundException 若指定用户不存在
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = appUserMapper.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("认证账号不存在: " + username));

        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!user.isEnabled())
                .roles(user.getRole())
                .build();
    }
}
