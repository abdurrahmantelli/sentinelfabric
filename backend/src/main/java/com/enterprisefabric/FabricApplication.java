package com.enterprisefabric;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class FabricApplication {
    public static void main(String[] args) {
        SpringApplication.run(FabricApplication.class, args);
    }
}
