package com.vini.typeahead.repository;

import com.vini.typeahead.entity.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    // READ path (Stage 2/3): top-5 for a prefix
    List<Suggestion> findTop5ByPrefixOrderByFrequencyDesc(String prefix);

    // WRITE path (Stage 4): find an existing (prefix, term) row to update during flush
    Optional<Suggestion> findByPrefixAndTerm(String prefix, String term);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE suggestion SET frequency = floor(frequency / :factor)", nativeQuery = true)
    int applyTimeDecay(@Param("factor") double factor);
}