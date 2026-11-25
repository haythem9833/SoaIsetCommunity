package com.example.demo.dto;

import com.example.demo.model.Commentaire;

public record CommentDTO(
        Integer id,
        String contenu,
        String date,
        Integer authorId,
        String authorName) {

    public static CommentDTO fromEntity(Commentaire c) {
        return new CommentDTO(
                c.getId(),
                c.getContenu(),
                c.getDate() == null ? null : c.getDate().toString(),
                c.getUser().getId(),
                c.getUser().getFullName());
    }
}
