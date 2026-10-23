package com.railway.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("users")
public class UserDocument {
    @Id public String id;
    @Indexed(unique = true) public String userId;
    public String name, email, phone;
    /** pbkdf2-sha256$iterations$salt$hash. Null for accounts created before login existed: they cannot sign in. */
    public String passwordHash;
    public java.time.Instant createdAt = java.time.Instant.now();
}
