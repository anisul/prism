# Semantic Search Middleware (Prism)

A Spring Boot middleware that sits between a client and a search API, using Google Gemini
to extract structured search intent from natural language queries — turning vague user input
into clean, filterable search parameters.

---

## How It Works

```
Client Query (natural language)
         │
         ▼
┌─────────────────────────────────┐
│     SearchProxyController       │
│       GET /api/search?q=        │
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│    QueryTransformerService      │
│    (Gemini gemini-2.0-flash)    │
└────────────────┬────────────────┘
                 │
        confidence >= 0.4?
                 │
        ┌────────┴─────────┐
       YES                 NO
        │                  │
        ▼                  ▼
  Structured filters   Raw passthrough
  forwarded upstream   query unchanged
        │                  │
        └────────┬─────────┘
                 ▼
┌─────────────────────────────────┐
│       SearchApiClient           │
│   (WebClient → upstream API)    │
└─────────────────────────────────┘
```

---

## Value Contrast

What the upstream search API receives — with and without the middleware:

| Natural Language Input | Without Middleware | With Middleware |
|---|---|---|
| `cheap red Nike shoes` | `q=cheap+red+Nike+shoes` | `q=shoes&color=red&brand=nike&category=footwear&price_range=budget` |
| `large blue sofa` | `q=large+blue+sofa` | `q=sofa&color=blue&category=furniture` |
| `premium Apple electronics` | `q=premium+Apple+electronics` | `brand=apple&category=electronics&price_range=premium` |
| `I want a black t-shirt` | `q=I+want+a+black+t-shirt` | `q=t-shirt&color=black&category=clothing` |
| `affordable Samsung phone` | `q=affordable+Samsung+phone` | `q=phone&brand=samsung&category=electronics&price_range=budget` |
| `luxury black Sony headphones` | `q=luxury+black+Sony+headphones` | `q=headphones&color=black&brand=sony&category=electronics&price_range=luxury` |
| `laptop` | `q=laptop` | `q=laptop` *(passthrough — no filters extractable)* |
| `good stuff` | `q=good+stuff` | `q=good+stuff` *(passthrough — confidence below threshold)* |

---

## Evaluation Results

Evaluated against a golden dataset of 25 handcrafted cases covering:

| Category | Count | Examples |
|---|---|---|
| Single filter | 5 | `"red shoes"`, `"Nike products"` |
| Multi-filter | 8 | `"cheap large blue sofa"`, `"premium Apple electronics"` |
| Implicit filter | 4 | `"something cosy for winter"` → category:clothing |
| No filter (passthrough) | 4 | `"laptop"`, `"gift ideas"` |
| Ambiguous / low confidence | 4 | `"good stuff"`, `"show me everything"` |

### Aggregate Scores

| Metric | Score |
|---|---|
| Avg Precision | *update after full eval run* |
| Avg Recall | *update after full eval run* |
| F1 Score | *update after full eval run* |
| Perfect cases (✅) | *update after full eval run* |

> Run `./gradlew eval` to populate these scores. See [Running the Evaluator](#running-the-evaluator) below.

### Partial Results (7 cases — rate limit during initial run)

| Input | Precision | Recall | Confidence | Status |
|---|---|---|---|---|
| `red shoes` | 1.00 | 1.00 | 0.85 | ✅ Perfect |
| `Nike products` | 1.00 | 1.00 | 0.85 | ✅ searchText fix applied |
| `something blue` | 1.00 | 1.00 | 0.50 | ✅ searchText fix applied |
| `show me furniture` | 1.00 | 1.00 | 0.90 | ✅ searchText fix applied |
| `I want a black t-shirt` | 1.00 | 1.00 | 0.90 | ✅ Perfect |
| `cheap large blue sofa` | 1.00 | 0.75 | 0.95 | ⚠️ `size:L` excluded (furniture descriptor, not clothing size) |
| `premium Apple electronics` | 1.00 | 1.00 | 0.95 | ✅ searchText fix applied |

---

## Filter Catalog

The middleware extracts filters from the following allowlist. Any value the LLM produces
that is not in this catalog is silently dropped before forwarding upstream.

| Key | Allowed Values |
|---|---|
| `category` | `electronics`, `clothing`, `footwear`, `furniture`, `books`, `sports`, `beauty`, `toys` |
| `brand` | `nike`, `apple`, `samsung`, `sony`, `ikea`, `adidas`, `zara`, `lego` |
| `color` | `red`, `blue`, `green`, `black`, `white`, `yellow`, `pink`, `grey` |
| `size` | `XS`, `S`, `M`, `L`, `XL`, `XXL` *(clothing and footwear only)* |
| `price_range` | `budget`, `mid`, `premium`, `luxury` |

### Synonym Normalisation

The system prompt teaches Gemini to normalise natural language to catalog values:

| User says | Extracted as |
|---|---|
| `"cheap"`, `"affordable"`, `"inexpensive"` | `price_range:budget` |
| `"expensive"`, `"high-end"` | `price_range:premium` |
| `"luxury"`, `"designer"` | `price_range:luxury` |
| `"mid range"`, `"moderate"` | `price_range:mid` |
| `"large"`, `"big"` *(clothing/footwear)* | `size:L` |
| `"small"`, `"tiny"` *(clothing/footwear)* | `size:S` |
| `"medium"` *(clothing/footwear)* | `size:M` |

---

## Architecture

```
src/main/java/com/grasshopper/
├── config/
│   └── SearchProperties.java              # confidence threshold + api base url
├── client/
│   └── SearchApiClient.java               # WebClient wrapper for upstream API
├── controller/
│   └── SearchProxyController.java         # GET /api/search?q=
├── domain/
│   ├── FilterEntry.java                   # key + value record
│   ├── SearchIntent.java                  # searchText + filters + confidence
│   ├── SearchApiRequest.java              # upstream request + toQueryString()
│   └── TransformResult.java              # intent + llm latency ms
├── logging/
│   ├── RequestIdFilter.java               # MDC request ID per request
│   └── SearchRequestLog.java             # structured log record
└── service/
    ├── FilterCatalogService.java          # interface (swap impl without changing callers)
    ├── InMemoryFilterCatalogService.java  # in-memory implementation
    └── QueryTransformerService.java       # Gemini-backed transformer + validator

src/test/
├── java/com/grasshopper/
│   ├── service/
│   │   └── QueryTransformerSmokeTest.java       # quick 4-case sanity check
│   └── eval/
│       ├── GoldenDataset.java                   # YAML binding model
│       ├── EvalResult.java                      # per-case result record
│       └── SearchIntentEvaluationTest.java      # @Tag("eval") full evaluator
└── resources/
    └── golden-dataset.yml                       # 25 golden cases
```

---

## Response Headers

Every response from `/api/search` includes these debug headers:

| Header | Example Value | Meaning |
|---|---|---|
| `X-Request-Id` | `a3f9c1b2` | Correlation ID — matches all log lines for this request |
| `X-Search-Transformed` | `true` | Whether the query was transformed or passed through |
| `X-Search-Confidence` | `0.95` | Gemini confidence score for the extraction |
| `X-Search-Filters` | `[color:red, brand:nike]` | Extracted and validated filters |
| `X-Search-Latency-Ms` | `1243` | Time taken for the Gemini API call |

---

## Runtime Logging

Every request produces a structured log line correlated by request ID:

```
10:42:31 [a3f9c1b2] INFO  SearchProxyController - Incoming query: 'cheap red Nike shoes'
10:42:32 [a3f9c1b2] INFO  SearchProxyController - [SEARCH] requestId=a3f9c1b2 | raw="cheap red Nike shoes" | searchText="shoes" | filters=[color:red, brand:nike, category:footwear, price_range:budget] | confidence=0.95 | mode=TRANSFORMED | llmMs=1243
10:42:32 [a3f9c1b2] INFO  SearchApiClient       - Upstream request: /search?q=shoes&color=red&brand=nike&category=footwear&price_range=budget
```

---

## Configuration

```yaml
search:
  confidence-threshold: 0.4      # queries below this score are passed through unchanged
  api:
    base-url: https://your-search-api.com

spring:
  ai:
    google:
      genai:
        api-key: ${GEMINI_API_KEY}
        chat:
          options:
            model: gemini-2.0-flash

logging:
  pattern:
    console: "%d{HH:mm:ss} [%X{requestId}] %-5level %logger{36} - %msg%n"
  level:
    root: INFO
    com.grasshopper: DEBUG
```

---

## Running the App

```bash
export GEMINI_API_KEY=your_key_here
./gradlew bootRun
```

Test with curl:

```bash
# Transformed — filters extracted, structured query forwarded
curl -v "http://localhost:8080/api/search?q=cheap+red+Nike+shoes"

# Passthrough — confidence below threshold, raw query forwarded
curl -v "http://localhost:8080/api/search?q=good+stuff"
```

---

## Running the Evaluator

The evaluator is tagged `@Tag("eval")` and runs separately from the normal test suite
to avoid hitting the Gemini API on every build.

```bash
GEMINI_API_KEY=your_key ./gradlew eval
```

Sample output:

```
╔══════════════════════════════════════════════════════════════╗
║           SEMANTIC SEARCH — INTENT EXTRACTION EVAL          ║
╚══════════════════════════════════════════════════════════════╝

✅  "cheap red Nike shoes"
    Expected filters : [color:red, brand:nike, category:footwear, price_range:budget]
    Actual filters   : [color:red, brand:nike, category:footwear, price_range:budget]
    Precision: 1.00 | Recall: 1.00 | Confidence: 0.95

⚠️  "something cosy for winter"
    Expected filters : [category:clothing]
    Actual filters   : []
    ❌ Missed         : [category:clothing]
    Precision: 1.00 | Recall: 0.00 | Confidence: 0.20

╔══════════════════════════════════════════════════════════════╗
║                        SUMMARY                              ║
╠══════════════════════════════════════════════════════════════╣
║  Cases evaluated  : 25                                       ║
║  Perfect (✅)      : 19                                       ║
║  Avg Precision    : 0.91                                     ║
║  Avg Recall       : 0.84                                     ║
║  F1 Score         : 0.87                                     ║
╚══════════════════════════════════════════════════════════════╝
```

---

## Stack

| Component | Version             |
|---|---------------------|
| Java | 25                  |
| Spring Boot | 3.4.x               |
| Spring AI | 1.1.x               |
| Gemini model | `gemini-2.0-flash`  |
| Build tool | Gradle (Groovy DSL) |