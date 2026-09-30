package com.vini.typeahead.repository;

import com.vini.typeahead.entity.SearchTerm;
import org.springframework.data.jpa.repository.JpaRepository;
//HM1 access
public interface SearchTermRepository extends JpaRepository<SearchTerm, String> {
}