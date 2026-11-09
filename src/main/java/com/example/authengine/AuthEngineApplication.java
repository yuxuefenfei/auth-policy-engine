package com.example.authengine;

import com.example.authengine.config.AuthProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties.class)
public class AuthEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthEngineApplication.class, args);
    }
}
