package com.videochat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.videochat.mapper")
public class VideoChatApplication {
    public static void main(String[] args) {
        SpringApplication.run(VideoChatApplication.class, args);
    }
}
