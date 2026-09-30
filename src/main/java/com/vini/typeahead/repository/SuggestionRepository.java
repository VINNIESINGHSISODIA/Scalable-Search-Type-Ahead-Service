package com.vini.typeahead.repository;

import com.vini.typeahead.entity.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    // READ path (Stage 2/3): top-5 for a prefix
    List<Suggestion> findTop5ByPrefixOrderByFrequencyDesc(String prefix);

    // WRITE path (Stage 4): find an existing (prefix, term) row to update during flush
    Optional<Suggestion> findByPrefixAndTerm(String prefix, String term);
}