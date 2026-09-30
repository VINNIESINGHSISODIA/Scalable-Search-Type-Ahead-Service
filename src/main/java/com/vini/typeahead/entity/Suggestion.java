package com.vini.typeahead.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
//HM2: prefix → term + frequency
@Entity
@Table(
        name = "suggestion",
        uniqueConstraints = @UniqueConstraint(columnNames = {"prefix", "term"}),
        indexes = @Index(name = "idx_prefix_freq", columnList = "prefix, frequency")
)
@Getter
@Setter
@NoArgsConstructor      // required by JPA
public class Suggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 512)
    private String prefix;

    @Column(nullable = false, length = 512)
    private String term;

    @Column(nullable = false)
    private long frequency;

    // Custom constructor: id is auto-generated, so we don't take it here.
    public Suggestion(String prefix, String term, long frequency) {
        this.prefix = prefix;
        this.term = term;
        this.frequency = frequency;
    }
}