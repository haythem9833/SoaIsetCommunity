package com.example.demo.dto;

import com.example.demo.model.Publication;

import java.util.List;

public record PublicationResponseDTO(
        Integer id,
        String title,
        String content,
        String createdAt,
        Integer authorId,
        String authorName,
        String authorType,
        Integer communityId,
        String communityName,
        String fileName,
        List<CommentDTO> comments) {

    public static PublicationResponseDTO fromEntity(Publication p) {
        return new PublicationResponseDTO(
                p.getId(),
                p.getTitle(),
                p.getContent(),
                p.getCreatedAt() == null ? null : p.getCreatedAt().toString(),
                p.getUser().getId(),
                p.getUser().getFullName(),
                UserResponseDTO.fromEntity(p.getUser()).getUserType(),
                p.getCommunity() == null ? null : p.getCommunity().getId(),
                p.getCommunity() == null ? null : p.getCommunity().getName(),
                p.getOriginalFileName(),
                p.getCommentaires().stream().map(CommentDTO::fromEntity).toList());
    }
}
