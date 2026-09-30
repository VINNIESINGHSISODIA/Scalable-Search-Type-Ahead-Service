package com.vini.typeahead.service;

import com.vini.typeahead.entity.Suggestion;
import com.vini.typeahead.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SuggestionService {

    private static final int MIN_PREFIX = 3;
    private static final int TOP_N = 5;
    private static final String KEY_PREFIX = "ta:sug:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final SuggestionRepository suggestionRepo;
    private final StringRedisTemplate redis;   // auto-configured by Spring Boot

    public List<String> getSuggestions(String prefix) {
        String p = prefix == null ? "" : prefix.trim().toLowerCase();

        // Rule: nothing before the 3rd character
        if (p.length() < MIN_PREFIX) {
            return List.of();
        }

        String key = KEY_PREFIX + p;

        // 1) Try the cache — sorted set, highest score (frequency) first
        Set<String> cached = redis.opsForZSet().reverseRange(key, 0, TOP_N - 1);
        if (cached != null && !cached.isEmpty()) {
            System.out.println("CACHE HIT  -> " + p);
            return new ArrayList<>(cached);
        }

        // 2) Cache miss — read HM2 from Postgres
        System.out.println("CACHE MISS -> " + p);
        List<Suggestion> rows = suggestionRepo.findTop5ByPrefixOrderByFrequencyDesc(p);

        // 3) Populate the cache: ZADD each term with score = frequency, then set a TTL
        for (Suggestion s : rows) {
            redis.opsForZSet().add(key, s.getTerm(), s.getFrequency());
        }
        if (!rows.isEmpty()) {
            redis.expire(key, CACHE_TTL);
        }

        return rows.stream().map(Suggestion::getTerm).toList();
    }
}