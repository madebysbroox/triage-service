package com.example.triage;

import com.example.triage.config.TriageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TriageProperties.class)
public class TriageServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TriageServiceApplication.class, args);
    }
}
