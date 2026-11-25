package com.example.demo.repository;

import com.example.demo.model.Community;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommunityRepository extends JpaRepository<Community, Integer> {

    // Communautés dont l'utilisateur est membre (utile avant de supprimer un compte)
    List<Community> findByMembersContaining(User user);
}
