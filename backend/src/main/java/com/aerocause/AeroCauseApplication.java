package com.aerocause;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AeroCause — AI-assisted air-quality decision support.
 *
 * <p>IMPORTANT: This system is DECISION SUPPORT ONLY.
 * Final verification and enforcement decisions belong to qualified officers.
 * Synthetic/sample data is clearly labelled in all API responses and UI views.
 */
@SpringBootApplication
@EnableScheduling
public class AeroCauseApplication {
    public static void main(String[] args) {
        SpringApplication.run(AeroCauseApplication.class, args);
    }
}
