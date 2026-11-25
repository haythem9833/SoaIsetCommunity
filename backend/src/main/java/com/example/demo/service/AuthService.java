package com.example.demo.service;

import com.example.demo.dto.LoginRequestDTO;
import com.example.demo.dto.AuthResponseDTO;
import com.example.demo.model.User;
import com.example.demo.model.UserStatus;
import com.example.demo.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponseDTO login(LoginRequestDTO request) {

        // 1. Vérifier les champs
        if (request.getEmail() == null ||
                request.getPassword() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email et mot de passe obligatoires"
            );
        }

        // 2. Rechercher l'utilisateur
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Email ou mot de passe incorrect"
                ));
        // 3. Vérifier le mot de passe
        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Email ou mot de passe incorrect"
            );
        }

        // 4. Vérifier le statut du compte
        if (user.getStatus() == UserStatus.REJECTED) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Votre inscription a été refusée"
            );
        }
        if (user.getStatus() != UserStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Votre compte n'est pas encore approuvé"
            );
        }
        // 5. Générer le token JWT
        String token = jwtService.generateToken(user.getEmail());

        // 6. Retourner le token
        return new AuthResponseDTO(
                token,
                "Connexion réussie"
        );
    }
}