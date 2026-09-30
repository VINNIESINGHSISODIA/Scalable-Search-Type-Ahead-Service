package com.vini.typeahead.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
//HM1: term → frequency
@Entity
@Table(name = "search_term")
@Getter
@Setter
@NoArgsConstructor      // required by JPA
@AllArgsConstructor     // gives the (term, frequency) constructor
public class SearchTerm {

    @Id
    @Column(length = 512)
    private String term;

    @Column(nullable = false)
    private long frequency;
}