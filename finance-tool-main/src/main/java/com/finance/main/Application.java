package com.finance.main;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.finance.web","com.finance.service"})
@MapperScan("com.finance.dao.mapper") // 如果你有 mapper 包，顺便加上
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
