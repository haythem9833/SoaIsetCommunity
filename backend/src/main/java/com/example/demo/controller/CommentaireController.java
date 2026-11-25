package com.example.demo.controller;

import com.example.demo.dto.CommentDTO;
import com.example.demo.dto.CommentRequestDTO;
import com.example.demo.model.User;
import com.example.demo.service.CommentaireService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CommentaireController {

    private final CommentaireService commentaireService;

    public CommentaireController(CommentaireService commentaireService) {
        this.commentaireService = commentaireService;
    }

    // Ajouter un commentaire à une publication
    @PostMapping("/publications/{id}/comments")
    public ResponseEntity<CommentDTO> add(
            @AuthenticationPrincipal User current,
            @PathVariable("id") Integer publicationId,
            @RequestBody CommentRequestDTO request) {

        CommentDTO created = commentaireService.add(publicationId, current.getId(), request.contenu());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal User current,
            @PathVariable("id") Integer commentId) {
        commentaireService.delete(commentId, current.getId());
        return ResponseEntity.noContent().build();
    }
}
