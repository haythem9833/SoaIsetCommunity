package com.example.demo.dto;

import com.example.demo.model.Community;
import com.example.demo.model.PrivateCommunity;
import org.hibernate.Hibernate;

public record CommunityResponseDTO(
        Integer id,
        String name,
        String type,
        String ownerName,
        Integer ownerId,
        int membersCount,
        Integer nbMax,
        boolean member) {

    public static CommunityResponseDTO fromEntity(Community entity, Integer currentUserId) {
        Community c = (Community) Hibernate.unproxy(entity);
        boolean isPrivate = c instanceof PrivateCommunity;
        return new CommunityResponseDTO(
                c.getId(),
                c.getName(),
                isPrivate ? "PRIVATE" : "PUBLIC",
                c.getUser().getFullName(),
                c.getUser().getId(),
                c.getMembers().size(),
                isPrivate ? ((PrivateCommunity) c).getNbMax() : null,
                c.getMembers().stream().anyMatch(m -> m.getId().equals(currentUserId)));
    }
}
