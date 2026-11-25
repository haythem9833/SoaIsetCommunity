package com.example.demo.config;

import com.example.demo.model.Admin;
import com.example.demo.model.UserStatus;
import com.example.demo.repository.AdminRepository;
import com.example.demo.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

// L'inscription refuse le type ADMIN et seul un admin peut approuver les comptes :
// sans admin au départ, personne ne pouvait jamais se connecter. On le crée donc au 1er démarrage.
@Component
public class AdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminInitializer(AdminRepository adminRepository,
                            UserRepository userRepository,
                            PasswordEncoder passwordEncoder,
                            @Value("${app.admin.email:admin@iset.rnu.tn}") String email,
                            @Value("${app.admin.password:ChangeMe123!}") String password) {
        this.adminRepository = adminRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (adminRepository.count() > 0) {
            return;
        }
        if (userRepository.existsByEmail(email)) {
            log.warn("Aucun admin, mais l'email {} est déjà utilisé par un autre compte : admin non créé.", email);
            return;
        }
        Admin admin = new Admin();
        admin.setFullName("Administrateur");
        admin.setDepartement("Administration");
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setStatus(UserStatus.APPROVED);
        adminRepository.save(admin);

        log.warn("Compte administrateur créé : {} (pensez à changer le mot de passe par défaut)", email);
    }
}
