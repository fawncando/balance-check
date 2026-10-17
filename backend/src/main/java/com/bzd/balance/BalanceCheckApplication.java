package com.bzd.balance;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.bzd.balance")
public class BalanceCheckApplication {

    public static void main(String[] args) {
        SpringApplication.run(BalanceCheckApplication.class, args);
    }
}
