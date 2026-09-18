package com.vasu.cardanalyzer.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    private AuthProvider provider = AuthProvider.LOCAL;

    private String googleId;

    private Role role = Role.ROLE_USER;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
