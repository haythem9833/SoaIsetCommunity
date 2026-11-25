package com.example.demo.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.*;

// Communauté ouverte : tout utilisateur approuvé peut la rejoindre librement.
@Entity
@DiscriminatorValue("PUBLIC")
@Getter
@Setter
public class PublicCommunity extends Community {
}
