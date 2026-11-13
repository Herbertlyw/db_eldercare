package edu.bjfu.eldercare.application.port;

public interface Crypto {

    String hash(String s);

    String encrypt(String s);

    String decrypt(String value);
}
