package com.acme.salary.auth;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "hr_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HrUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HrRole role;
}
