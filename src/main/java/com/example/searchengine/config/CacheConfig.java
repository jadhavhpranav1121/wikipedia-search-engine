package com.example.searchengine.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    public static final String SEARCH_CACHE = "searchResults";

    private final SearchProperties searchProperties;

    public CacheConfig(SearchProperties searchProperties) {
        this.searchProperties = searchProperties;
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(SEARCH_CACHE);
        cacheManager.setCaffeine(caffeineCacheSpec());
        return cacheManager;
    }

    @Bean
    public Caffeine<Object, Object> caffeineCacheSpec() {
        return Caffeine.newBuilder()
                .expireAfterWrite(searchProperties.getCache().getTtlMinutes(), TimeUnit.MINUTES)
                .maximumSize(searchProperties.getCache().getMaxSize())
                .recordStats();
    }
}
