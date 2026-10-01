package com.vini.typeahead.scheduler;

import com.vini.typeahead.repository.SearchTermRepository;
import com.vini.typeahead.repository.SuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class TimeDecayJob {

    private final SearchTermRepository termRepo;
    private final SuggestionRepository suggestionRepo;
    private final StringRedisTemplate redis;

    @Value("${typeahead.decay.factor}")
    private double factor;

    @Scheduled(cron = "${typeahead.decay.cron}")
    @Transactional
    public void decay() {
        int terms = termRepo.applyTimeDecay(factor);        // HM1
        int rows  = suggestionRepo.applyTimeDecay(factor);  // HM2

        // clear the read cache so stale rankings are dropped
        Set<String> keys = redis.keys("ta:sug:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }

        System.out.println("TIME DECAY ÷" + factor + " applied to "
                + terms + " terms and " + rows + " suggestion rows; cache cleared");
    }
}
