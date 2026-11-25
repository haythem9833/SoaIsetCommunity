package com.example.demo.service;

import com.example.demo.model.*;
import com.example.demo.repository.CommunityRepository;
import com.example.demo.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final AccessService access;
    private final String emailDomain;

    public UserService(UserRepository userRepository,
                       CommunityRepository communityRepository,
                       PasswordEncoder passwordEncoder,
                       FileStorageService fileStorageService,
                       AccessService access,
                       @Value("${app.email-domain:@iset.rnu.tn}") String emailDomain) {
        this.userRepository = userRepository;
        this.communityRepository = communityRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.access = access;
        this.emailDomain = emailDomain.toLowerCase(Locale.ROOT);
    }

    // Inscription d'un étudiant ou professeur (le compte reste PENDING)
    public User registerUser(User user, String userType) {

        if (user.getFullName() == null || user.getFullName().isBlank()) {
            throw badRequest("Le nom complet est obligatoire");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw badRequest("L'email est obligatoire");
        }
        if (user.getPassword() == null || user.getPassword().length() < 8) {
            throw badRequest("Le mot de passe doit contenir au moins 8 caractères");
        }

        // Vérifier le domaine ISET
        String email = user.getEmail().trim().toLowerCase(Locale.ROOT);
        if (!email.endsWith(emailDomain)) {
            throw badRequest("Seules les adresses email ISET (" + emailDomain + ") sont acceptées");
        }

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette adresse email existe déjà");
        }

        if (userType == null) {
            throw badRequest("Le type de compte est obligatoire");
        }

        User newUser;
        switch (userType.trim().toUpperCase()) {
            case "STUDENT":
                newUser = new Student();
                break;
            case "PROF":
                newUser = new Prof();
                break;
            default:
                // ADMIN n'est jamais accepté à l'inscription
                throw badRequest("Type de compte invalide (STUDENT ou PROF)");
        }

        newUser.setFullName(user.getFullName().trim());
        newUser.setDepartement(user.getDepartement());
        newUser.setEmail(email);
        newUser.setPassword(passwordEncoder.encode(user.getPassword()));
        newUser.setStatus(UserStatus.PENDING);

        return userRepository.save(newUser);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
    }

    public List<User> getPendingUsers() {
        return userRepository.findByStatus(UserStatus.PENDING);
    }

    public User updateUserStatus(Integer id, UserStatus status) {
        User user = getUserById(id);
        if (access.isAdmin(user)) {
            throw badRequest("Le statut d'un administrateur ne peut pas être modifié");
        }
        user.setStatus(status);
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Integer id) {
        User user = getUserById(id);
        if (access.isAdmin(user)) {
            throw badRequest("Un administrateur ne peut pas être supprimé");
        }

        // 1. Supprimer les fichiers des publications de l'utilisateur
        user.getPublications().forEach(p -> fileStorageService.delete(p.getStoredFileName()));

        // 2. Supprimer les fichiers des publications des communautés qu'il a créées
        user.getCommunities().forEach(c ->
                c.getPublications().forEach(p -> fileStorageService.delete(p.getStoredFileName())));

        // 3. Le retirer des communautés dont il est simple membre
        communityRepository.findByMembersContaining(user)
                .forEach(c -> c.getMembers().removeIf(m -> m.getId().equals(user.getId())));

        // 4. Suppression du compte (publications, commentaires et communautés en cascade)
        userRepository.delete(user);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
