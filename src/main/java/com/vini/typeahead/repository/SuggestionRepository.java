package com.vini.typeahead.repository;

import com.vini.typeahead.entity.Suggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
//HM2 access — the top-5 query
public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    // Spring Data reads this method NAME and generates the query:
    // SELECT * FROM suggestion WHERE prefix = ? ORDER BY frequency DESC LIMIT 5
    List<Suggestion> findTop5ByPrefixOrderByFrequencyDesc(String prefix);
}