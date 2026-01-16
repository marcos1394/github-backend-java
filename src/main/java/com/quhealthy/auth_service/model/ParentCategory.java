package com.quhealthy.auth_service.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@Entity
@Table(name = "parent_categories")
public class ParentCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    // Relación 1 a Muchos con CategoryProvider
    // "mappedBy" indica que la dueña de la relación es la clase hija (CategoryProvider)
    @OneToMany(mappedBy = "parentCategory", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude // Evita bucles infinitos al imprimir logs
    private List<CategoryProvider> categories;
}