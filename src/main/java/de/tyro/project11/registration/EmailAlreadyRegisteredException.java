package de.tyro.project11.registration;

public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() {
        super("Diese E-Mail-Adresse ist bereits registriert.");
    }
}
