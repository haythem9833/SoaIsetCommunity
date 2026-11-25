package com.example.demo.service;

import com.example.demo.dto.CommunityRequestDTO;
import com.example.demo.dto.CommunityResponseDTO;
import com.example.demo.model.*;
import com.example.demo.repository.CommunityRepository;
import com.example.demo.repository.UserRepository;

import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CommunityService {

    private final CommunityRepository communityRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final AccessService access;

    public CommunityService(CommunityRepository communityRepository,
                            UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            FileStorageService fileStorageService,
                            AccessService access) {
        this.communityRepository = communityRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<CommunityResponseDTO> list(Integer userId) {
        return communityRepository.findAll().stream()
                .map(c -> CommunityResponseDTO.fromEntity(c, userId))
                .toList();
    }

    @Transactional
    public CommunityResponseDTO create(Integer userId, CommunityRequestDTO dto) {
        User user = getUser(userId);

        // Règle choisie : seuls les professeurs et l'administrateur créent des communautés
        if (!access.isProfOrAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seuls les professeurs et l'administrateur peuvent créer une communauté");
        }
        if (dto.name() == null || dto.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nom est obligatoire");
        }
        if (dto.type() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le type (PUBLIC ou PRIVATE) est obligatoire");
        }

        Community community;
        switch (dto.type().trim().toUpperCase()) {
            case "PUBLIC":
                community = new PublicCommunity();
                community.setIsPrivate(false);
                break;
            case "PRIVATE":
                if (dto.key() == null || dto.key().length() < 4) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Une communauté privée nécessite une clé d'accès (4 caractères minimum)");
                }
                if (dto.nbMax() == null || dto.nbMax() < 1) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Le nombre maximum de membres doit être au moins 1");
                }
                PrivateCommunity privateCommunity = new PrivateCommunity();
                privateCommunity.setAccessKey(passwordEncoder.encode(dto.key()));
                privateCommunity.setNbMax(dto.nbMax());
                privateCommunity.setIsPrivate(true);
                community = privateCommunity;
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type de communauté invalide");
        }

        community.setName(dto.name().trim());
        community.setUser(user);
        community.getMembers().add(user); // le créateur est membre

        return CommunityResponseDTO.fromEntity(communityRepository.save(community), userId);
    }

    @Transactional
    public CommunityResponseDTO join(Integer communityId, Integer userId, String key) {
        User user = getUser(userId);
        Community community = getCommunity(communityId);

        if (access.isMember(community, user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vous êtes déjà membre");
        }

        if (Hibernate.unproxy(community) instanceof PrivateCommunity privateCommunity) {
            if (key == null || !passwordEncoder.matches(key, privateCommunity.getAccessKey())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Clé d'accès incorrecte");
            }
            if (privateCommunity.getNbMax() != null
                    && community.getMembers().size() >= privateCommunity.getNbMax()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La communauté est complète");
            }
        }

        community.getMembers().add(user);
        return CommunityResponseDTO.fromEntity(communityRepository.save(community), userId);
    }

    @Transactional
    public void leave(Integer communityId, Integer userId) {
        User user = getUser(userId);
        Community community = getCommunity(communityId);

        if (community.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le créateur ne peut pas quitter sa communauté (il peut la supprimer)");
        }
        community.getMembers().removeIf(m -> m.getId().equals(user.getId()));
    }

    @Transactional
    public void delete(Integer communityId, Integer userId) {
        User user = getUser(userId);
        Community community = getCommunity(communityId);

        boolean owner = community.getUser().getId().equals(userId);
        if (!owner && !access.isAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seul le créateur ou l'administrateur peut supprimer la communauté");
        }

        // Les publications sont supprimées en cascade : on supprime aussi leurs fichiers
        community.getPublications().forEach(p -> fileStorageService.delete(p.getStoredFileName()));
        communityRepository.delete(community);
    }

    private User getUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
    }

    private Community getCommunity(Integer id) {
        return communityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Communauté introuvable"));
    }
}
