package com.recycllux.pelanode;

import com.recycllux.pelanode.config.PelanodeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(PelanodeProperties.class)
public class PelanodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PelanodeApplication.class, args);
    }
}
