package com.vini.typeahead.repository;

import com.vini.typeahead.entity.SearchTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
//HM1 access
public interface SearchTermRepository extends JpaRepository<SearchTerm, String> {
    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE search_term SET frequency = floor(frequency / :factor)", nativeQuery = true)
    int applyTimeDecay(@Param("factor") double factor);
}