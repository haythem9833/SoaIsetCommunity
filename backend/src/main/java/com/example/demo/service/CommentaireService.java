package com.example.demo.service;

import com.example.demo.dto.CommentDTO;
import com.example.demo.model.Commentaire;
import com.example.demo.model.Publication;
import com.example.demo.model.User;
import com.example.demo.repository.CommentaireRepository;
import com.example.demo.repository.PublicationRepository;
import com.example.demo.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
public class CommentaireService {

    private final CommentaireRepository commentaireRepository;
    private final PublicationRepository publicationRepository;
    private final UserRepository userRepository;
    private final AccessService access;

    public CommentaireService(CommentaireRepository commentaireRepository,
                              PublicationRepository publicationRepository,
                              UserRepository userRepository,
                              AccessService access) {
        this.commentaireRepository = commentaireRepository;
        this.publicationRepository = publicationRepository;
        this.userRepository = userRepository;
        this.access = access;
    }

    @Transactional
    public CommentDTO add(Integer publicationId, Integer userId, String contenu) {
        User user = getUser(userId);
        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publication introuvable"));

        if (!access.canView(user, publication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé");
        }
        if (contenu == null || contenu.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le commentaire ne peut pas être vide");
        }
        if (contenu.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Commentaire trop long (2000 caractères max)");
        }

        Commentaire commentaire = new Commentaire();
        commentaire.setContenu(contenu.trim());
        commentaire.setDate(LocalDateTime.now());
        commentaire.setUser(user);
        commentaire.setPublication(publication);

        return CommentDTO.fromEntity(commentaireRepository.save(commentaire));
    }

    @Transactional
    public void delete(Integer commentId, Integer userId) {
        User user = getUser(userId);
        Commentaire commentaire = commentaireRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commentaire introuvable"));

        boolean owner = commentaire.getUser().getId().equals(user.getId());
        if (!owner && !access.isAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seul l'auteur ou l'administrateur peut supprimer ce commentaire");
        }

        // orphanRemoval=true sur Publication.commentaires supprime la ligne en base
        commentaire.getPublication().getCommentaires().remove(commentaire);
    }

    private User getUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
    }
}
