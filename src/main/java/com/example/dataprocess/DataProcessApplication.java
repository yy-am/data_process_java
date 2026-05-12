package com.example.dataprocess;

import com.example.dataprocess.infrastructure.runtime.SkillRuntimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 数据加工应用启动类。
 */
@SpringBootApplication
@EnableConfigurationProperties(SkillRuntimeProperties.class)
public class DataProcessApplication {

    /**
     * 启动 Spring Boot 应用。
     */
    public static void main(String[] args) {
        SpringApplication.run(DataProcessApplication.class, args);
    }
}
