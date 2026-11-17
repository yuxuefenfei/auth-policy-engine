package com.example.authengine.mapper;

import com.example.authengine.entity.PolicyEntity;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

/**
 * 策略主表数据访问接口 (MyBatis-Flex Mapper)
 * <p>
 * 管理各场景策略的当前生效指针与版本演进状态，支持行级互斥锁保障并发一致性。
 */
@Mapper
public interface PolicyMapper extends BaseMapper<PolicyEntity> {

    /**
     * 根据策略唯一代码查询策略主记录
     *
     * @param code 策略代码 (如 WEB_LOGIN)
     * @return 策略主表实体 Optional 包装
     */
    default Optional<PolicyEntity> findByPolicyCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        QueryWrapper query = QueryWrapper.create()
                .from(PolicyEntity.class)
                .where("policy_code = ?", code);
        return Optional.ofNullable(selectOneByQuery(query));
    }

    /**
     * 使用排他行级锁 (FOR UPDATE) 锁定指定策略记录，防止策略版本升级并发竞争
     *
     * @param code 策略唯一代码
     * @return 锁定的策略实体 Optional 包装
     */
    default Optional<PolicyEntity> lockByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        QueryWrapper query = QueryWrapper.create()
                .from(PolicyEntity.class)
                .where("policy_code = ?", code)
                .forUpdate();
        return Optional.ofNullable(selectOneByQuery(query));
    }
}
