package com.vini.typeahead.repository;

import com.vini.typeahead.entity.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    // READ: top-5 for a prefix
    List<Suggestion> findByPrefixOrderByFrequencyDesc(String prefix);

    // WRITE / promotion helpers
    Optional<Suggestion> findByPrefixAndTerm(String prefix, String term);
    long countByPrefix(String prefix);
    Optional<Suggestion> findFirstByPrefixOrderByFrequencyAsc(String prefix);  // the weakest of the stored 5

    // TIME DECAY
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE suggestion SET frequency = floor(frequency / :factor)", nativeQuery = true)
    int applyTimeDecay(@Param("factor") double factor);
}