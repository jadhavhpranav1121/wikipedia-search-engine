package com.example.searchengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private final SearchProperties searchProperties;

    public AsyncConfig(SearchProperties searchProperties) {
        this.searchProperties = searchProperties;
    }

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(searchProperties.getIndexing().getThreadCount());
        executor.setMaxPoolSize(searchProperties.getIndexing().getThreadCount() * 2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-indexer-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Bean(name = "indexingExecutorService")
    public ExecutorService indexingExecutorService() {
        return Executors.newFixedThreadPool(
                searchProperties.getIndexing().getThreadCount(),
                r -> {
                    Thread t = new Thread(r, "batch-indexer");
                    t.setDaemon(true);
                    return t;
                }
        );
    }
}
