package com.vini.typeahead.service;
// read logic
import com.vini.typeahead.entity.Suggestion;
import com.vini.typeahead.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
//This is your read path now: normalize the prefix → enforce the 3-char rule → one indexed query to HM2 → map the Suggestion rows to their term strings.
@Service
@RequiredArgsConstructor     // Lombok injects the final repository
public class SuggestionService {

    private static final int MIN_PREFIX = 3;

    private final SuggestionRepository suggestionRepo;

    public List<String> getSuggestions(String prefix) {
        String p = prefix == null ? "" : prefix.trim().toLowerCase();

        // Rule: nothing before the 3rd character
        if (p.length() < MIN_PREFIX) {
            return List.of();
        }

        // HM2 lookup: top-5 by frequency
        return suggestionRepo.findTop5ByPrefixOrderByFrequencyDesc(p)
                .stream()
                .map(Suggestion::getTerm)
                .toList();
    }
}