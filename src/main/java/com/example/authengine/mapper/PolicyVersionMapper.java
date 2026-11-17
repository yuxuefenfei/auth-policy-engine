package com.example.authengine.mapper;

import com.example.authengine.entity.PolicyVersionEntity;
import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

/**
 * 策略版本快照数据访问接口 (MyBatis-Flex Mapper)
 * <p>
 * 提供对不可变策略语法树快照的落库、校验及历史版本倒序检索功能。
 */
@Mapper
public interface PolicyVersionMapper extends BaseMapper<PolicyVersionEntity> {

    /**
     * 根据策略代码及具体版本号查询唯一的版本快照
     *
     * @param code    策略代码
     * @param version 版本号
     * @return 对应的策略版本实体 Optional 包装
     */
    default Optional<PolicyVersionEntity> findByPolicyCodeAndVersionNumber(String code, int version) {
        QueryWrapper query = QueryWrapper.create()
                .from(PolicyVersionEntity.class)
                .where("policy_code = ?", code)
                .and("version_number = ?", version);
        return Optional.ofNullable(selectOneByQuery(query));
    }

    /**
     * 查询指定策略的所有版本历史记录，按版本号倒序排列
     *
     * @param code 策略代码
     * @return 按版本号降序排列的历史版本实体列表
     */
    default List<PolicyVersionEntity> findAllByPolicyCodeOrderByVersionNumberDesc(String code) {
        QueryWrapper query = QueryWrapper.create()
                .from(PolicyVersionEntity.class)
                .where("policy_code = ?", code)
                .orderBy("version_number", false);
        return selectListByQuery(query);
    }
}
