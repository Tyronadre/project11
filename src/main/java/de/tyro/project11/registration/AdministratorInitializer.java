package de.tyro.project11.registration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdministratorInitializer implements ApplicationRunner {

    private final UserRepository users;

    public AdministratorInitializer(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Preserve the bootstrap for installations created before admin roles existed.
        // Hibernate has updated the schema before application runners execute.
        if (users.countByAdminTrue() == 0) {
            users.findFirstByOrderByIdAsc().ifPresent(user -> user.setAdmin(true));
        }
    }
}
