package com.example.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.*;

// Communauté fermée : on la rejoint avec une clé d'accès, dans la limite de nbMax membres.
@Entity
@DiscriminatorValue("PRIVATE")
@Getter
@Setter
public class PrivateCommunity extends Community {

    // "key" est un mot réservé en SQL : on nomme la colonne autrement
    @Column(name = "access_key")
    private String accessKey;   // stockée hachée (BCrypt)

    private Integer nbMax;
}
