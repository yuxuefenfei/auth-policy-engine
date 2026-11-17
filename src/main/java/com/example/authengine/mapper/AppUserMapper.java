package com.example.authengine.mapper;

import com.example.authengine.entity.AppUser;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

/**
 * 应用用户数据访问接口 (MyBatis-Flex Mapper)
 * <p>
 * 提供针对用户实体基础 CRUD 以及基于用户名的高效检索能力。
 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {

    /**
     * 根据用户名精确查询单个用户信息
     *
     * @param username 登录用户名
     * @return 匹配的用户实体 Optional 包装，若不存在则返回 empty
     */
    default Optional<AppUser> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        QueryWrapper query = QueryWrapper.create()
                .from(AppUser.class)
                .eq(AppUser::getUsername, username.trim());
        return Optional.ofNullable(selectOneByQuery(query));
    }
}
