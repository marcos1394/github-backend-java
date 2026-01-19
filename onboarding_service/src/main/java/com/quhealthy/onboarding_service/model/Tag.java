package com.quhealthy.onboarding_service.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tags") // Mapea a la misma tabla física que usa Auth Service
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column()
    private String color;

    // 🔗 RELACIÓN INVERSA
    // Es vital para que Hibernate entienda la conexión bidireccional.
    // Usamos @JsonIgnore para que al pedir un Tag, no te traiga la lista gigante de médicos.
    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore 
    private Set<Provider> providers = new HashSet<>();
}