package com.vini.typeahead.bootstrap;

import com.vini.typeahead.entity.SearchTerm;
import com.vini.typeahead.entity.Suggestion;
import com.vini.typeahead.repository.SearchTermRepository;
import com.vini.typeahead.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.*;
//seeds HM1, derives HM2
@Component
@RequiredArgsConstructor     // Lombok generates the constructor for the final fields
public class DataSeeder implements ApplicationRunner {

    private static final int MIN_PREFIX = 3;

    private final SearchTermRepository termRepo;
    private final SuggestionRepository suggestionRepo;

    private static final Map<String, Long> SEED = new LinkedHashMap<>() {{
        put("microsoft", 101000L);
        put("michael obama", 72000L);
        put("microwave", 70000L);
        put("microsoft office", 58000L);
        put("michael jackson", 50000L);
        put("micromax", 41000L);
        put("microphone", 37000L);
        put("microservices", 33000L);
        put("cat videos", 105000L);
        put("category", 60000L);
        put("catapult", 58000L);
        put("caterpillar", 52000L);
        put("cats and dogs", 40000L);
        put("cats as pets", 30000L);
        put("google", 250000L);
        put("google maps", 180000L);
        put("google translate", 140000L);
        put("goodreads", 60000L);
        put("spring boot", 88000L);
        put("spring security", 40000L);
    }};

    @Override
    public void run(ApplicationArguments args) {
        if (termRepo.count() > 0) {
            System.out.println("Data already seeded, skipping.");
            return;
        }

        // 1) Seed HM1 (term -> frequency)
        SEED.forEach((term, freq) -> termRepo.save(new SearchTerm(term, freq)));

        // 2) Derive HM2 — every prefix of length >= 3 for each term
        List<Suggestion> rows = new ArrayList<>();
        SEED.forEach((term, freq) -> {
            for (int len = MIN_PREFIX; len <= term.length(); len++) {
                rows.add(new Suggestion(term.substring(0, len), term, freq));
            }
        });
        suggestionRepo.saveAll(rows);

        System.out.println("Seeded " + SEED.size() + " terms into HM1 and "
                + rows.size() + " rows into HM2.");
    }
}