package com.example.demo.model;


import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED) // ou SINGLE_TABLE / TABLE_PER_CLASS
@DiscriminatorColumn(name = "user_type")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String fullName;

    @Column(unique = true)
    private String email;

    private String password;
    private String departement;
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.PENDING;

    // Relations
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Publication> publications = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Commentaire> commentaires = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Community> communities = new ArrayList<>();

    // equals/hashCode sur l'id uniquement : @Data incluait les collections (User <-> Publication <-> Community...)
    // => StackOverflowError dès qu'un User était ajouté à un HashSet (community.getMembers().add(user)).
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return User.class.hashCode();
    }
}
