package com.vini.typeahead.service;

import com.vini.typeahead.entity.SearchTerm;
import com.vini.typeahead.entity.Suggestion;
import com.vini.typeahead.repository.SearchTermRepository;
import com.vini.typeahead.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class FrequencyService {

    private static final int MIN_PREFIX = 3;
    private static final String PENDING_KEY = "ta:pending";   // Redis hash: term -> pending count
    private static final String CACHE_PREFIX = "ta:sug:";      // read-cache keys to invalidate

    private final StringRedisTemplate redis;
    private final SearchTermRepository termRepo;
    private final SuggestionRepository suggestionRepo;

    @Value("${typeahead.write.threshold}")
    private long threshold;

    @Value("${typeahead.write.sampling-rate}")
    private double samplingRate;

    public enum Outcome { DROPPED_BY_SAMPLING, PENDING, FLUSHED }

    /** Called once per completed search (on Enter). */
    public Outcome recordSearch(String rawTerm) {
        String term = rawTerm == null ? "" : rawTerm.trim().toLowerCase();
        if (term.isEmpty()) {
            return Outcome.DROPPED_BY_SAMPLING;
        }

        // 1) Sampling gate — accept only ~samplingRate of writes
        if (ThreadLocalRandom.current().nextDouble() >= samplingRate) {
            System.out.println("DROPPED (sampling) -> " + term);
            return Outcome.DROPPED_BY_SAMPLING;
        }

        // 2) Bump the pending counter in Redis (fast, NO database write yet)
        Long pending = redis.opsForHash().increment(PENDING_KEY, term, 1L);
        System.out.println("PENDING " + pending + "/" + threshold + " -> " + term);

        // 3) Threshold check — only now do we touch the database
        if (pending != null && pending >= threshold) {
            flushTerm(term);
            return Outcome.FLUSHED;
        }
        return Outcome.PENDING;
    }

    /** Flush a term: update HM1, refresh its HM2 rows, invalidate the read cache. */
    @Transactional
    public void flushTerm(String term) {
        Object raw = redis.opsForHash().get(PENDING_KEY, term);
        long delta = raw == null ? 0 : Long.parseLong(raw.toString());
        if (delta <= 0) {
            return;
        }

        // reset the pending bucket first
        redis.opsForHash().put(PENDING_KEY, term, "0");

        // 1) HM1: term -> frequency  (+= delta, create if new)
        SearchTerm st = termRepo.findById(term).orElse(new SearchTerm(term, 0));
        st.setFrequency(st.getFrequency() + delta);
        termRepo.save(st);
        long newFreq = st.getFrequency();

        // 2) HM2: update this term's frequency across all its prefixes,
        //    and invalidate the read cache for each so next read repopulates
        for (int len = MIN_PREFIX; len <= term.length(); len++) {
            String prefix = term.substring(0, len);

            Suggestion s = suggestionRepo.findByPrefixAndTerm(prefix, term)
                    .orElse(new Suggestion(prefix, term, 0));
            s.setFrequency(newFreq);
            suggestionRepo.save(s);

            redis.delete(CACHE_PREFIX + prefix);   // cache invalidation
        }

        System.out.println("FLUSHED '" + term + "'  (+" + delta + " -> " + newFreq + ")");
    }
}