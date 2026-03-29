package com.grasshopper.prism.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.grasshopper.prism.domain.FilterEntry;
import com.grasshopper.prism.domain.TransformResult;
import com.grasshopper.prism.service.QueryTransformerService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Tag("eval")
@SpringBootTest
class SearchIntentEvaluationTest {

    @Autowired
    QueryTransformerService transformer;

    @Test
    void evaluateGoldenDataset() throws Exception {
        var dataset = loadDataset();
        List<EvalResult> results = new ArrayList<>();

        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           SEMANTIC SEARCH — INTENT EXTRACTION EVAL          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        for (GoldenDataset.GoldenCase testCase : dataset.cases()) {
            TransformResult transformResult = transformer.transform(testCase.input());
            EvalResult result = evaluate(testCase, transformResult);
            results.add(result);
            printCaseResult(result);
        }

        printSummary(results);
    }


    private EvalResult evaluate(GoldenDataset.GoldenCase testCase, TransformResult transformResult) {
        var intent = transformResult.intent();
        var expected = testCase.expectedFilters();
        var actual = intent.filters();

        List<GoldenDataset.ExpectedFilter> correct = expected.stream()
                .filter(e -> actual.stream().anyMatch(a ->
                        a.key().equalsIgnoreCase(e.key()) &&
                                a.value().equalsIgnoreCase(e.value())))
                .toList();

        List<GoldenDataset.ExpectedFilter> missed = expected.stream()
                .filter(e -> actual.stream().noneMatch(a ->
                        a.key().equalsIgnoreCase(e.key()) &&
                                a.value().equalsIgnoreCase(e.value())))
                .toList();

        List<FilterEntry> hallucinated = actual.stream()
                .filter(a -> expected.stream().noneMatch(e ->
                        e.key().equalsIgnoreCase(a.key()) &&
                                e.value().equalsIgnoreCase(a.value())))
                .toList();

        double precision = actual.isEmpty() ? 1.0
                : (double) correct.size() / actual.size();

        double recall = expected.isEmpty() ? 1.0
                : (double) correct.size() / expected.size();

        boolean searchTextMatch = normalise(testCase.expectedSearchText())
                .equals(normalise(intent.searchText()));

        return new EvalResult(
                testCase.input(),
                testCase.expectedSearchText(),
                intent.searchText(),
                expected,
                actual,
                correct,
                missed,
                hallucinated,
                searchTextMatch,
                precision,
                recall,
                intent.confidence(),
                testCase.notes()
        );
    }

    private String normalise(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    // ── Printing ─────────────────────────────────────────────────────

    private void printCaseResult(EvalResult r) {
        String status = r.perfect() ? "✅" : "⚠️ ";
        System.out.printf("%-2s  \"%s\"%n", status, r.input());
        System.out.printf("    Expected filters : %s%n", r.expectedFilters());
        System.out.printf("    Actual filters   : %s%n", r.actualFilters());

        if (!r.missedFilters().isEmpty()) {
            System.out.printf("    ❌ Missed         : %s%n", r.missedFilters());
        }
        if (!r.hallucinatedFilters().isEmpty()) {
            System.out.printf("    👻 Hallucinated   : %s%n", r.hallucinatedFilters());
        }
        if (!r.searchTextMatch()) {
            System.out.printf("    📝 SearchText     : expected=\"%s\" actual=\"%s\"%n",
                    r.expectedSearchText(), r.actualSearchText());
        }

        System.out.printf("    Precision: %.2f | Recall: %.2f | Confidence: %.2f%n",
                r.precision(), r.recall(), r.confidence());
        System.out.printf("    Note: %s%n%n", r.notes());
    }

    private void printSummary(List<EvalResult> results) {
        double avgPrecision = results.stream()
                .mapToDouble(EvalResult::precision).average().orElse(0);
        double avgRecall = results.stream()
                .mapToDouble(EvalResult::recall).average().orElse(0);
        double f1 = (avgPrecision + avgRecall) > 0
                ? 2 * (avgPrecision * avgRecall) / (avgPrecision + avgRecall)
                : 0;
        long perfectCount = results.stream().filter(EvalResult::perfect).count();

        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                        SUMMARY                               ║");
        System.out.println("╠══════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Cases evaluated  : %-38d ║%n", results.size());
        System.out.printf( "║  Perfect (✅)      : %-38d ║%n", perfectCount);
        System.out.printf( "║  Avg Precision    : %-38s ║%n", String.format("%.2f", avgPrecision));
        System.out.printf( "║  Avg Recall       : %-38s ║%n", String.format("%.2f", avgRecall));
        System.out.printf( "║  F1 Score         : %-38s ║%n", String.format("%.2f", f1));
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
    }

    // ── Dataset loader ───────────────────────────────────────────────

    private GoldenDataset loadDataset() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();
        try (InputStream is = getClass().getResourceAsStream("/golden-dataset.yml")) {
            return mapper.readValue(is, GoldenDataset.class);
        }
    }
}