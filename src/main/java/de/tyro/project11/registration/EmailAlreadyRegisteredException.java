package de.tyro.project11.registration;

public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() {
        super("This email address is already registered.");
    }
}
