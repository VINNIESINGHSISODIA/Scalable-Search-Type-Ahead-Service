package com.vini.typeahead.web;

import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1")
public class SuggestionController {

    private static final int MIN_PREFIX = 3;   // suggestions only from the 3rd character
    private static final int TOP_N = 5;         // return top-5

    // Temporary in-memory HM2:  prefix -> (term -> frequency).
    // Replaced by Postgres (durable) + Redis (cache) in later stages.
    private final Map<String, Map<String, Integer>> suggestions = new ConcurrentHashMap<>(Map.of(
            "mic", Map.of(
                    "microsoft", 101000,
                    "michael obama", 72000,
                    "microwave", 70000,
                    "microsoft office", 58000,
                    "michael jackson", 50000,
                    "micromax", 41000          // 6 candidates -> one gets dropped by top-5
            ),
            "cat", Map.of(
                    "cat videos", 105000,
                    "category", 60000,
                    "catapult", 58000,
                    "caterpillar", 52000,
                    "cats and dogs", 40000,
                    "cats as pets", 30000
            )
    ));

    @GetMapping("/suggestions")
    public List<String> getSuggestions(@RequestParam String prefix) {
        String p = prefix.trim().toLowerCase();

        // Rule 1: nothing before the 3rd character
        if (p.length() < MIN_PREFIX) {
            return List.of();
        }

        // Rank by frequency (highest first), then take top-5.
        return suggestions.getOrDefault(p, Map.of())
                .entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_N)
                .map(Map.Entry::getKey)
                .toList();
    }
}