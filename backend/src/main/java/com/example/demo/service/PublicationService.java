package com.example.demo.service;

import com.example.demo.dto.PublicationRequestDTO;
import com.example.demo.dto.PublicationResponseDTO;
import com.example.demo.model.*;
import com.example.demo.repository.CommunityRepository;
import com.example.demo.repository.PublicationRepository;
import com.example.demo.repository.UserRepository;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PublicationService {

    private final PublicationRepository publicationRepository;
    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final FileStorageService fileStorageService;
    private final AccessService access;

    public PublicationService(PublicationRepository publicationRepository,
                              UserRepository userRepository,
                              CommunityRepository communityRepository,
                              FileStorageService fileStorageService,
                              AccessService access) {
        this.publicationRepository = publicationRepository;
        this.userRepository = userRepository;
        this.communityRepository = communityRepository;
        this.fileStorageService = fileStorageService;
        this.access = access;
    }

    // Résultat d'un téléchargement : le fichier + son nom d'origine
    public record FileDownload(Resource resource, String fileName) {}

    // Fil d'actualité (filtrable par communauté)
    @Transactional(readOnly = true)
    public List<PublicationResponseDTO> list(Integer userId, Integer communityId) {
        User user = getUser(userId);
        return publicationRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(p -> access.canView(user, p))
                .filter(p -> communityId == null
                        || (p.getCommunity() != null && p.getCommunity().getId().equals(communityId)))
                .map(PublicationResponseDTO::fromEntity)
                .toList();
    }

    @Transactional
    public PublicationResponseDTO create(Integer userId, String title, String content,
                                         Integer communityId, MultipartFile file) {
        User user = getUser(userId);

        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le titre est obligatoire");
        }
        if (title.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Titre trop long (200 caractères max)");
        }
        if (content != null && content.length() > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contenu trop long (5000 caractères max)");
        }

        Publication publication = new Publication();
        publication.setTitle(title.trim());
        publication.setContent(content == null ? "" : content.trim());
        publication.setCreatedAt(LocalDateTime.now());
        publication.setUser(user);

        if (communityId != null) {
            Community community = communityRepository.findById(communityId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Communauté introuvable"));
            if (!access.isAdmin(user) && !access.isMember(community, user)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Vous devez être membre de la communauté pour y publier");
            }
            publication.setCommunity(community);
        }

        if (file != null && !file.isEmpty()) {
            publication.setOriginalFileName(file.getOriginalFilename());
            publication.setStoredFileName(fileStorageService.store(file));
        }

        return PublicationResponseDTO.fromEntity(publicationRepository.save(publication));
    }

    @Transactional
    public PublicationResponseDTO update(Integer id, Integer userId, PublicationRequestDTO dto) {
        User user = getUser(userId);
        Publication publication = getPublication(id);
        checkOwnerOrAdmin(user, publication);

        if (dto.title() == null || dto.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le titre est obligatoire");
        }
        publication.setTitle(dto.title().trim());
        publication.setContent(dto.content() == null ? "" : dto.content().trim());

        return PublicationResponseDTO.fromEntity(publicationRepository.save(publication));
    }

    @Transactional
    public void delete(Integer id, Integer userId) {
        User user = getUser(userId);
        Publication publication = getPublication(id);
        checkOwnerOrAdmin(user, publication);

        fileStorageService.delete(publication.getStoredFileName());
        publicationRepository.delete(publication);
    }

    @Transactional(readOnly = true)
    public FileDownload download(Integer id, Integer userId) {
        User user = getUser(userId);
        Publication publication = getPublication(id);

        if (!access.canView(user, publication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé");
        }
        if (publication.getStoredFileName() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cette publication n'a pas de fichier");
        }
        return new FileDownload(
                fileStorageService.load(publication.getStoredFileName()),
                publication.getOriginalFileName());
    }

    // ---------- méthodes utilitaires ----------

    private void checkOwnerOrAdmin(User user, Publication publication) {
        boolean owner = publication.getUser().getId().equals(user.getId());
        if (!owner && !access.isAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Seul l'auteur ou l'administrateur peut effectuer cette action");
        }
    }

    private User getUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable"));
    }

    private Publication getPublication(Integer id) {
        return publicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publication introuvable"));
    }
}
