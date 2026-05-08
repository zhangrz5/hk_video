package com.example.hkvideo;

import com.example.hkvideo.config.HikvisionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(HikvisionProperties.class)
public class HkVideoApplication {

    public static void main(String[] args) {
        SpringApplication.run(HkVideoApplication.class, args);
    }
}
