package com.vini.typeahead.controller;

import com.vini.typeahead.service.FrequencyService;
import com.vini.typeahead.service.SuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService suggestionService;
    private final FrequencyService frequencyService;

    // READ path
    @GetMapping("/suggestions")
    public List<String> getSuggestions(@RequestParam String prefix) {
        return suggestionService.getSuggestions(prefix);
    }

    // WRITE path — fired on Enter (a completed search)
    @PostMapping("/frequency")
    public Map<String, Object> updateFrequency(@RequestBody Map<String, String> body) {
        String query = body.get("query");
        FrequencyService.Outcome outcome = frequencyService.recordSearch(query);
        return Map.of(
                "query", query == null ? "" : query,
                "outcome", outcome.name()
        );
    }
}