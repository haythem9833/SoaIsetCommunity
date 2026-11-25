package com.example.demo.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "community")
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@Setter
public class Community {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    private Boolean isPrivate;

    // Créateur de la communauté
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // Membres de la communauté (table de jointure community_members)
    @ManyToMany
    @JoinTable(name = "community_members",
            joinColumns = @JoinColumn(name = "community_id"),
            inverseJoinColumns = @JoinColumn(name = "member_id"))
    private Set<User> members = new HashSet<>();

    @OneToMany(mappedBy = "community", cascade = CascadeType.REMOVE)
    private List<Publication> publications = new ArrayList<>();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Community other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Community.class.hashCode();
    }
}
