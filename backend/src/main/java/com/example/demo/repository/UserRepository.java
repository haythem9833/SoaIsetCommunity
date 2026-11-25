package com.example.demo.repository;

import com.example.demo.model.User;
import com.example.demo.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);
    // Vérifier si un email existe déjà
    boolean existsByEmail(String email);

    // Récupérer les utilisateurs selon leur statut
    List<User> findByStatus(UserStatus status);
}
