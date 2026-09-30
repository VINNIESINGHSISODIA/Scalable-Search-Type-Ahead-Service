package com.vini.typeahead.controller;

import com.vini.typeahead.service.SuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
//the controller now just receives the request and hands off to SuggestionService, which reads from Postgres.
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor     // Lombok injects the final service
public class SuggestionController {

    private final SuggestionService suggestionService;

    @GetMapping("/suggestions")
    public List<String> getSuggestions(@RequestParam String prefix) {
        return suggestionService.getSuggestions(prefix);
    }
}