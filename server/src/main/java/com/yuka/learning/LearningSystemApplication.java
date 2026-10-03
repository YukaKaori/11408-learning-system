package com.yuka.learning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LearningSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningSystemApplication.class, args);
    }

}
