# Wikipedia Search Engine — Production-Grade Spring Boot 3

A production-quality full-text search engine over Wikipedia dumps using TF-IDF ranking,  
StAX streaming parsing, Caffeine caching, and a clean layered architecture.

---

## Project Structure

```
com.example.searchengine
├── SearchEngineApplication.java         # Entry point
│
├── config/
│   ├── SearchProperties.java            # Typed @ConfigurationProperties
│   ├── AsyncConfig.java                 # Thread pool + ExecutorService beans
│   └── CacheConfig.java                 # Caffeine cache manager
│
├── controller/
│   ├── SearchController.java            # GET /api/search
│   └── AdminController.java             # POST /api/admin/reindex, GET /api/admin/status
│
├── service/
│   ├── SearchService.java               # Query execution, pagination, caching
│   ├── IndexingService.java             # Full reindex pipeline orchestration
│   └── TfIdfScorer.java                 # AND-query TF-IDF scoring + title boost
│
├── repository/
│   └── InvertedIndexRepository.java     # Thread-safe in-memory index + doc store
│
├── indexing/
│   ├── WikiDumpParser.java              # StAX streaming BZ2 XML parser
│   └── IndexBuilder.java               # Batched parallel tokenization + index write
│
├── model/
│   ├── WikiDocument.java                # Core domain object
│   └── IndexingStatus.java             # Indexing lifecycle state
│
├── dto/
│   ├── SearchResponseDto.java           # Paginated search response
│   ├── SearchResultDto.java             # Single search result
│   ├── ReindexResponseDto.java          # Reindex trigger response
│   └── ErrorResponseDto.java           # Standardized error response
│
├── util/
│   ├── WikiMarkupCleaner.java           # Regex-based wiki markup removal
│   ├── Tokenizer.java                   # Lowercase, alphabetic tokenization
│   ├── StopWordFilter.java              # 60+ English stop words
│   └── SnippetGenerator.java           # Context-aware snippet extraction
│
└── exception/
    ├── GlobalExceptionHandler.java      # @ControllerAdvice — all exceptions
    ├── DumpFileNotFoundException.java
    ├── IndexingException.java
    └── SearchException.java
```

---

## Prerequisites

- Java 17+
- Maven 3.8+
- A Wikipedia XML dump in BZ2 format (e.g., `enwiki-latest-pages-articles.xml.bz2`)

### Downloading a Wikipedia Dump

```bash
# Full English dump (~22 GB compressed)
wget https://dumps.wikimedia.org/enwiki/latest/enwiki-latest-pages-articles.xml.bz2

# Smaller test dump (Simple English Wikipedia ~250 MB)
wget https://dumps.wikimedia.org/simplewiki/latest/simplewiki-latest-pages-articles.xml.bz2
```

---

## Configuration

Edit `src/main/resources/application.yml`:

```yaml
search:
  dump-path: /path/to/wiki.xml.bz2   # ← Set this to your .bz2 file path
  indexing:
    batch-size: 1000                  # Documents per indexing batch
    thread-count: 4                   # Parallel worker threads
    max-documents: -1                 # -1 = no limit; use 50000 for testing
  cache:
    ttl-minutes: 10
    max-size: 500
  ranking:
    title-boost-factor: 2.5           # Score multiplier for title matches
  snippet:
    length: 300                       # Characters per result snippet
```

---

## Build & Run

```bash
# Clone or unzip the project
cd wikipedia-search-engine

# Build (skip tests for first run)
mvn clean package -DskipTests

# Run
java -Xmx4g -jar target/wikipedia-search-engine-1.0.0.jar

# OR: Run with custom dump path
java -Xmx4g -jar target/wikipedia-search-engine-1.0.0.jar \
  --search.dump-path=/data/simplewiki.xml.bz2

# Run tests
mvn test
```

> **Memory recommendation**: Use `-Xmx4g` for Simple English Wikipedia, `-Xmx16g` for full English Wikipedia.

---

## API Usage

### 1. Trigger Reindex

```bash
curl -X POST http://localhost:8080/api/admin/reindex
```

Response (202 Accepted):
```json
{
  "status": "ACCEPTED",
  "message": "Reindexing started asynchronously. Check GET /api/admin/status for progress."
}
```

### 2. Check Indexing Status

```bash
curl http://localhost:8080/api/admin/status
```

Response (while running):
```json
{
  "state": "RUNNING",
  "documentsProcessed": 0,
  "startedAt": "2024-03-15T10:30:00Z"
}
```

Response (completed):
```json
{
  "state": "COMPLETED",
  "documentsProcessed": 847263,
  "durationMs": 184000,
  "memoryUsedMb": 3241,
  "startedAt": "2024-03-15T10:30:00Z",
  "completedAt": "2024-03-15T10:33:04Z"
}
```

### 3. Search

```bash
curl "http://localhost:8080/api/search?q=india+history&page=0&size=10"
```

Response (200 OK):
```json
{
  "totalResults": 120,
  "page": 0,
  "size": 10,
  "executionTimeMs": 12,
  "results": [
    {
      "documentId": 3632887,
      "title": "History of India",
      "score": 8.91,
      "snippet": "...India has one of the world's oldest civilizations. The Indus Valley Civilisation, which flourished..."
    },
    {
      "documentId": 14533,
      "title": "India",
      "score": 6.43,
      "snippet": "India, officially the Republic of India, is a country in South Asia. It is the seventh-largest..."
    },
    {
      "documentId": 1893712,
      "title": "Ancient India",
      "score": 5.12,
      "snippet": "...The history of ancient India spans thousands of years, from the earliest known human settlements..."
    }
  ]
}
```

### 4. Error Responses

Query with blank `q`:
```json
{
  "timestamp": "2024-03-15T10:35:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Required parameter 'q' is missing",
  "path": "/api/search"
}
```

Search before reindex:
```json
{
  "timestamp": "2024-03-15T10:35:00Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Index is empty. Please trigger a reindex via POST /api/admin/reindex.",
  "path": "/api/search"
}
```

---

## Architecture Decisions

### 1. StAX Streaming Parser (not DOM)
Wikipedia dumps are 20+ GB uncompressed. DOM loading would cause OOM. StAX fires  
events as bytes are read from the BZ2 stream — constant memory usage regardless of file size.

### 2. BZ2 Streaming (Apache Commons Compress)
`BZip2CompressorInputStream` wraps a `BufferedInputStream` over `FileInputStream`.  
No temporary file is written; decompression and parsing happen in a single pass.

### 3. ConcurrentHashMap for the Inverted Index
The index is a `ConcurrentHashMap<String, ConcurrentHashMap<Long, Integer>>`.  
Writes during batch indexing use `computeIfAbsent` + `merge` which are atomic.  
The outer map is replaced atomically via `synchronized replaceIndex()` at the end of  
a reindex cycle — reads during reindex see the old index, never a partial state.

### 4. Atomic Index Swap (Zero Downtime Reindex)
New index structures are built entirely in memory during reindex.  
`InvertedIndexRepository.replaceIndex()` swaps all three volatile references  
inside a `synchronized` block. Ongoing searches complete against the old index.

### 5. TF-IDF with Smoothed IDF
`IDF(t) = log((N+1)/(DF(t)+1)) + 1` — additive smoothing prevents division by zero  
for tokens appearing in every document. The `+1` outside the log keeps IDF ≥ 1.

### 6. AND Query Semantics
Multi-word queries require all tokens to appear in a document. The intersection of  
posting lists is computed iteratively — the first empty intersection short-circuits.

### 7. Title Boosting
If a query token appears in the document title, its TF-IDF contribution is multiplied  
by `title-boost-factor` (default 2.5). Title matches indicate higher relevance.

### 8. Batch + Parallel Indexing
Documents are grouped into batches of `batch-size`. Each batch is submitted as  
parallel tasks to a fixed-size `ExecutorService`. This saturates CPU cores during  
the tokenization phase (typically the bottleneck).

### 9. Caffeine Cache
Search results are cached by `"query|page|size"` composite key.  
Cache is invalidated after every successful reindex to prevent stale results.  
TTL and max size are configurable.

### 10. Constructor Injection Only
All Spring beans use constructor injection — no `@Autowired` field injection.  
This enables straightforward unit testing with `new Service(mockDep1, mockDep2)`.

---

## Performance Expectations

| Dataset                    | Docs      | Index Time | Memory   |
|---------------------------|-----------|------------|----------|
| Simple English Wikipedia  | ~200,000  | ~3 min     | ~1.5 GB  |
| Full English Wikipedia    | ~6.8M     | ~45 min    | ~14 GB   |

Query latency (after indexing): typically 5–50 ms, cached responses < 1 ms.

---

## Running Tests

```bash
mvn test
```

Test coverage includes:
- `WikiMarkupCleanerTest` — 9 test cases for all markup patterns
- `TokenizerTest` — stop word removal, lowercasing, punctuation handling
- `InvertedIndexRepositoryTest` — thread-safe reads/writes and index swap
- `TfIdfScorerTest` — scoring, AND semantics, title boost
- `SearchServiceTest` — pagination, empty index handling, page slicing
- `SnippetGeneratorTest` — context centering, ellipsis, null/blank handling
#   w i k i p e d i a - s e a r c h - e n g i n e  
 