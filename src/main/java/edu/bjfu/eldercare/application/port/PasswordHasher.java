package edu.bjfu.eldercare.application.port;

public interface PasswordHasher {

    String encode(String raw);

    boolean matches(String raw, String encoded);
}
