package com.example.authengine.policy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "auth_policy_version", uniqueConstraints =
@UniqueConstraint(columnNames = {"policy_code", "version_number"}))
@Getter
@Setter
@NoArgsConstructor
public class PolicyVersionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "policy_code", nullable = false, length = 80)
    private String policyCode;
    @Column(name = "version_number", nullable = false)
    private int versionNumber;
    @Lob
    @Column(name = "definition", nullable = false, columnDefinition = "LONGTEXT")
    private String definition;
    @Column(nullable = false, length = 64)
    private String checksum;
    @Column(nullable = false, length = 24)
    private String status;
    private Instant createdAt;
    private Instant publishedAt;
}
