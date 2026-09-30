package com.eldercare.user;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "users", indexes = @Index(columnList = "username", unique = true))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(min = 3, max = 50)
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @NotBlank
    @Column(nullable = false)
    private String passwordHash; // BCrypt

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(length = 100)
    private String displayName;

    @Column(length = 30)
    private String emergencyContact; // phone of family/caregiver

    private boolean enabled = true;

    protected User() {}

    public User(String username, String passwordHash, Role role, String displayName) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.displayName = displayName;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public String getDisplayName() { return displayName; }
    public String getEmergencyContact() { return emergencyContact; }
    public boolean isEnabled() { return enabled; }

    public void setEmergencyContact(String emergencyContact) { this.emergencyContact = emergencyContact; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
