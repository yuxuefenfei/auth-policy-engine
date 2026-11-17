package com.example.authengine.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Flex 持久层自动化配置
 * <p>
 * 声明扫描 Mapper 接口所在包，并激活 MyBatis-Flex 核心特性支持。
 */
@Configuration
@MapperScan("com.example.authengine.mapper")
public class MyBatisFlexConfig {
}
