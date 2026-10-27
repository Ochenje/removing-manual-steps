package tech.kood.rental.security;

import com.password4j.Password;

/**
 * Turns passwords into hashes and checks passwords against hashes.
 * The plain password is never stored anywhere.
 */
public class PasswordHasher {

    // Grinds a plain password into a bcrypt hash.
    // Bcrypt adds a random "salt" itself, so the same password
    // gives a different hash every time.
    public String hash(String plainPassword) {
        requireNotNull(plainPassword);
        return Password.hash(plainPassword).withBcrypt().getResult();
    }

    // Checks a typed password against a stored hash.
    // Returns true only if they match.
    public boolean verify(String plainPassword, String storedHash) {
        requireNotNull(plainPassword);
        requireNotNull(storedHash);
        return Password.check(plainPassword, storedHash).withBcrypt();
    }

    // Stops a null from sneaking in. Blank checks belong to the validators, not here.
    private void requireNotNull(String value) {
        if (value == null) {
            throw new IllegalArgumentException("password value must not be null");
        }
    }
}