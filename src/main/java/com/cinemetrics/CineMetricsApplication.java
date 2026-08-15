package com.cinemetrics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CineMetricsApplication {
    public static void main(String[] args) {
        SpringApplication.run(CineMetricsApplication.class, args);
    }
}
