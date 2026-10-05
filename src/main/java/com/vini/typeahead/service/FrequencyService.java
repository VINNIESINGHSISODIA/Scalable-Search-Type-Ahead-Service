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

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class FrequencyService {

    private static final int MIN_PREFIX = 3;
    private static final int TOP_N = 5;
    private static final String PENDING_KEY = "ta:pending";
    private static final String CACHE_PREFIX = "ta:sug:";

    private final StringRedisTemplate redis;
    private final SearchTermRepository termRepo;
    private final SuggestionRepository suggestionRepo;

    @Value("${typeahead.write.threshold}")
    private long threshold;

    @Value("${typeahead.write.sampling-rate}")
    private double samplingRate;

    public enum Outcome {
        DROPPED_BY_SAMPLING,  // write ignored by the sampling gate (we only count a fraction of searches)
        PENDING,              // counted in the Redis buffer; not yet written to the DB (below threshold)
        FLUSHED               // threshold reached; flushed to Postgres (HM1 + HM2) and cache invalidated
    }

    public Outcome recordSearch(String rawTerm) {
        String term = rawTerm == null ? "" : rawTerm.trim().toLowerCase();
        if (term.isEmpty()) return Outcome.DROPPED_BY_SAMPLING;

        // Sampling gate
        if (ThreadLocalRandom.current().nextDouble() >= samplingRate) {
            System.out.println("DROPPED (sampling) -> " + term);
            return Outcome.DROPPED_BY_SAMPLING;
        }

        // Pending counter (Redis) — no DB write yet
        Long pending = redis.opsForHash().increment(PENDING_KEY, term, 1L);
        System.out.println("PENDING " + pending + "/" + threshold + " -> " + term);

        if (pending != null && pending >= threshold) {
            flushTerm(term);
            return Outcome.FLUSHED;
        }
        return Outcome.PENDING;
    }

    @Transactional
    public void flushTerm(String term) {
        Object raw = redis.opsForHash().get(PENDING_KEY, term);
        long delta = raw == null ? 0 : Long.parseLong(raw.toString());
        if (delta <= 0) return;

        redis.opsForHash().put(PENDING_KEY, term, "0");

        // 1) HM1 — the FULL frequency universe: EVERY term lives here
        SearchTerm st = termRepo.findById(term).orElse(new SearchTerm(term, 0));
        st.setFrequency(st.getFrequency() + delta);
        termRepo.save(st);
        long newFreq = st.getFrequency();

        // 2) HM2 — keep EXACTLY the top-5 per prefix (promotion logic)
        for (int len = MIN_PREFIX; len <= term.length(); len++) {
            String prefix = term.substring(0, len);
            updatePrefixTop5(prefix, term, newFreq);
            redis.delete(CACHE_PREFIX + prefix);   // invalidate read cache
        }

        System.out.println("FLUSHED '" + term + "'  (+" + delta + " -> " + newFreq + ")");
    }

    /** Maintain the top-5 for ONE prefix when `term` reaches `newFreq`. */
    private void updatePrefixTop5(String prefix, String term, long newFreq) {
        // a) already among the stored 5 -> just refresh its frequency
        Optional<Suggestion> existing = suggestionRepo.findByPrefixAndTerm(prefix, term);
        if (existing.isPresent()) {
            Suggestion s = existing.get();
            s.setFrequency(newFreq);
            suggestionRepo.save(s);
            return;
        }

        // b) fewer than 5 stored -> room, just insert
        if (suggestionRepo.countByPrefix(prefix) < TOP_N) {
            suggestionRepo.save(new Suggestion(prefix, term, newFreq));
            return;
        }

        // c) top-5 full -> promote ONLY if better than the weakest
        Suggestion weakest = suggestionRepo.findFirstByPrefixOrderByFrequencyAsc(prefix).orElse(null);
        if (weakest != null && newFreq > weakest.getFrequency()) {
            suggestionRepo.delete(weakest);                              // demote (still safe in HM1!)
            suggestionRepo.save(new Suggestion(prefix, term, newFreq));  // promote
            System.out.println("  PROMOTED '" + term + "' into '" + prefix
                    + "' top-5, demoted '" + weakest.getTerm() + "'");
        }
        // else: not good enough for this prefix — do nothing
    }
}