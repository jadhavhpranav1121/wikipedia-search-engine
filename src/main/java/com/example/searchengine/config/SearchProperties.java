package com.example.searchengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "search")
public class SearchProperties {

    @NotBlank(message = "dump-path must not be blank")
    private String dumpPath;

    @NotNull
    private IndexingProperties indexing = new IndexingProperties();

    @NotNull
    private CacheProperties cache = new CacheProperties();

    @NotNull
    private RankingProperties ranking = new RankingProperties();

    @NotNull
    private SnippetProperties snippet = new SnippetProperties();

    @Data
    public static class IndexingProperties {
        @Min(1)
        private int batchSize = 1000;

        @Min(1)
        private int threadCount = 4;

        private int maxDocuments = -1;
    }

    @Data
    public static class CacheProperties {
        @Min(1)
        private int ttlMinutes = 10;

        @Min(10)
        private int maxSize = 500;
    }

    @Data
    public static class RankingProperties {
        private double titleBoostFactor = 2.5;
    }

    @Data
    public static class SnippetProperties {
        @Min(50)
        private int length = 300;
    }
}
