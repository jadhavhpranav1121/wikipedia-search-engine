package com.example.searchengine.service;

import com.example.searchengine.config.SearchProperties;
import com.example.searchengine.model.WikiDocument;
import com.example.searchengine.repository.InvertedIndexRepository;
import com.example.searchengine.util.Tokenizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Computes TF-IDF relevance scores for AND-mode multi-token queries.
 *
 * <p>Formula used:
 * <pre>
 *   TF(t, d)  = termFrequency(t, d) / tokenCount(d)         [normalized TF]
 *   IDF(t)    = log((N + 1) / (DF(t) + 1)) + 1              [smoothed IDF]
 *   score(d)  = sum over query tokens of TF(t,d) * IDF(t)
 * </pre>
 *
 * <p>Title Boost: if a query token appears in the document title,
 * a configurable multiplier is applied to its contribution.
 */
@Slf4j
@Component
public class TfIdfScorer {

    private final InvertedIndexRepository indexRepository;
    private final SearchProperties searchProperties;
    private final Tokenizer tokenizer;

    public TfIdfScorer(InvertedIndexRepository indexRepository,
                       SearchProperties searchProperties,
                       Tokenizer tokenizer) {
        this.indexRepository = indexRepository;
        this.searchProperties = searchProperties;
        this.tokenizer = tokenizer;
    }

    /**
     * Executes an AND-semantics TF-IDF query.
     *
     * <p>AND semantics: only documents containing ALL query tokens are returned.
     * Scores are computed across all tokens and summed per document.
     *
     * @param queryTokens list of tokenized query terms
     * @return list of (docId, score) pairs sorted by descending score
     */
    public List<Map.Entry<Long, Double>> score(List<String> queryTokens) {
        if (queryTokens.isEmpty()) {
            return Collections.emptyList();
        }

        long totalDocs = indexRepository.getTotalDocumentCount();
        if (totalDocs == 0) {
            log.warn("Scoring attempted on empty index.");
            return Collections.emptyList();
        }

        double titleBoost = searchProperties.getRanking().getTitleBoostFactor();

        // Step 1: Find the intersection of posting lists (AND semantics)
        Set<Long> candidateDocIds = null;
        for (String token : queryTokens) {
            Set<Long> postingDocIds = indexRepository.getPostingList(token).keySet();
            if (candidateDocIds == null) {
                candidateDocIds = new HashSet<>(postingDocIds);
            } else {
                candidateDocIds.retainAll(postingDocIds);
            }

            if (candidateDocIds.isEmpty()) {
                log.debug("AND query: no documents contain all tokens. First missing: {}", token);
                return Collections.emptyList();
            }
        }

        // Step 2: Compute scores for candidate documents
        Map<Long, Double> scores = new HashMap<>(candidateDocIds.size());

        for (String token : queryTokens) {
            int df = indexRepository.getDocumentFrequency(token);
            double idf = Math.log((double)(totalDocs + 1) / (df + 1)) + 1.0;

            Map<Long, Integer> postings = indexRepository.getPostingList(token);

            for (Long docId : candidateDocIds) {
                Integer rawTf = postings.get(docId);
                if (rawTf == null) continue;

                Optional<WikiDocument> docOpt = indexRepository.getDocument(docId);
                if (docOpt.isEmpty()) continue;

                WikiDocument doc = docOpt.get();
                int tokenCount = doc.getTokenCount() > 0 ? doc.getTokenCount() : 1;
                double tf = (double) rawTf / tokenCount;
                double contribution = tf * idf;

                // Apply title boost if token appears in the title
                if (titleContainsToken(doc.getTitle(), token)) {
                    contribution *= titleBoost;
                    log.debug("Title boost applied for token '{}' in doc '{}'", token, doc.getTitle());
                }

                scores.merge(docId, contribution, Double::sum);
            }
        }

        // Step 3: Sort by descending score
        List<Map.Entry<Long, Double>> results = new ArrayList<>(scores.entrySet());
        results.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        log.debug("Scored {} candidate documents for query: {}", results.size(), queryTokens);
        return results;
    }

    private boolean titleContainsToken(String title, String token) {
        if (title == null) return false;
        List<String> titleTokens = tokenizer.tokenizeQuery(title);
        return titleTokens.contains(token);
    }
}
