package com.quhealthy.auth_service.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@Entity
@Table(name = "category_providers")
public class CategoryProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    // Relación con el Padre (ParentCategory)
    @ManyToOne(fetch = FetchType.EAGER) // Eager porque casi siempre queremos saber el padre
    @JoinColumn(name = "parent_category_id", nullable = false)
    private ParentCategory parentCategory;

    // Relación con las Hijas (SubCategory)
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<SubCategory> subcategories;
}