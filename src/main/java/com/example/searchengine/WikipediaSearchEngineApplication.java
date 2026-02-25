package com.example.searchengine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@SpringBootApplication
@EnableCaching
@EnableAsync
public class WikipediaSearchEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(WikipediaSearchEngineApplication.class, args);
        log.info("Wikipedia Search Engine started successfully.");
    }
}
