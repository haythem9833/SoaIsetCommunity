package com.example.demo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
public class Publication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String title;

    @Column(length = 5000)
    private String content;

    private LocalDateTime createdAt;

    // Pièce jointe (optionnelle) : nom d'origine et nom réel sur le disque
    private String originalFileName;
    private String storedFileName;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // Communauté (optionnelle) : null = publication générale
    @ManyToOne
    @JoinColumn(name = "community_id")
    private Community community;

    @OneToMany(mappedBy = "publication", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Commentaire> commentaires = new ArrayList<>();
}
