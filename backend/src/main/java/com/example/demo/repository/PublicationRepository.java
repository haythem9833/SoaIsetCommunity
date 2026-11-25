package com.example.demo.repository;

import com.example.demo.model.Publication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublicationRepository extends JpaRepository<Publication, Integer> {

    // Les plus récentes d'abord
    List<Publication> findAllByOrderByCreatedAtDesc();
}
