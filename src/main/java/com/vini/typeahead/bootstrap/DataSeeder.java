package com.vini.typeahead.bootstrap;

import com.vini.typeahead.entity.SearchTerm;
import com.vini.typeahead.entity.Suggestion;
import com.vini.typeahead.repository.SearchTermRepository;
import com.vini.typeahead.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;

@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private static final int MIN_PREFIX = 3;
    private static final int TOP_N = 5;

    private final SearchTermRepository termRepo;
    private final SuggestionRepository suggestionRepo;

    @Value("${typeahead.seed-on-start:true}")
    private boolean seedOnStart;

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
        // Only one instance should seed (avoids a race when multiple replicas start together)
        if (!seedOnStart) {
            System.out.println("Seeding disabled for this instance; skipping.");
            return;
        }

        if (termRepo.count() > 0) {
            System.out.println("Data already seeded, skipping.");
            return;
        }

        // 1) HM1 — every term
        SEED.forEach((term, freq) -> termRepo.save(new SearchTerm(term, freq)));

        // 2) HM2 — compute top-5 per prefix
        Map<String, List<Suggestion>> byPrefix = new HashMap<>();
        SEED.forEach((term, freq) -> {
            for (int len = MIN_PREFIX; len <= term.length(); len++) {
                String prefix = term.substring(0, len);
                byPrefix.computeIfAbsent(prefix, k -> new ArrayList<>())
                        .add(new Suggestion(prefix, term, freq));
            }
        });

        List<Suggestion> rows = new ArrayList<>();
        for (List<Suggestion> candidates : byPrefix.values()) {
            candidates.sort(Comparator.comparingLong(Suggestion::getFrequency).reversed());
            candidates.stream().limit(TOP_N).forEach(rows::add);
        }
        suggestionRepo.saveAll(rows);

        System.out.println("Seeded " + SEED.size() + " terms into HM1 and "
                + rows.size() + " rows into HM2 (<=5 per prefix).");
    }
}