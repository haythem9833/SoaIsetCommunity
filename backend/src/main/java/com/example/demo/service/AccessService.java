package com.example.demo.service;

import com.example.demo.model.*;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

// Règles d'accès communes aux services (rôles, appartenance aux communautés).
@Component
public class AccessService {

    public boolean isAdmin(User user) {
        return Hibernate.unproxy(user) instanceof Admin;
    }

    public boolean isProfOrAdmin(User user) {
        return Hibernate.unproxy(user) instanceof Prof; // Admin hérite de Prof
    }

    public boolean isMember(Community community, User user) {
        return community.getMembers().stream()
                .anyMatch(m -> m.getId().equals(user.getId()));
    }

    // Une communauté publique est visible par tous ; une privée seulement par ses membres et l'admin.
    public boolean canView(User user, Publication publication) {
        Community community = publication.getCommunity();
        if (community == null) {
            return true;
        }
        if (!(Hibernate.unproxy(community) instanceof PrivateCommunity)) {
            return true;
        }
        return isAdmin(user) || isMember(community, user);
    }
}
