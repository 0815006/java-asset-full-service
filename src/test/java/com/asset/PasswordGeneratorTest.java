package com.asset;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordGeneratorTest {
    @Test
    public void generateHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String password = "ILike88Door";
        String hash = encoder.encode(password);
        System.out.println("BCRYPT_HASH_START:" + hash + ":BCRYPT_HASH_END");
    }
}
