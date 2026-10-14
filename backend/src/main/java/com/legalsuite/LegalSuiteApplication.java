package com.legalsuite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// JWT only: no form login, so Boot must not create a default in-memory user with a generated password.
@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
public class LegalSuiteApplication {
    public static void main(String[] args) {
        SpringApplication.run(LegalSuiteApplication.class, args);
    }
}
