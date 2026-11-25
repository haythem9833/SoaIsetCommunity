package com.example.demo.repository;

import com.example.demo.model.Commentaire;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentaireRepository extends JpaRepository<Commentaire, Integer> {}
