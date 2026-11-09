package com.example.authengine.policy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "auth_policy", uniqueConstraints = @UniqueConstraint(columnNames = "policy_code"))
@Getter
@Setter
@NoArgsConstructor
public class PolicyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "policy_code", nullable = false, length = 80)
    private String policyCode;
    @Column(name = "active_version", nullable = false)
    private int activeVersion;
    @Column(name = "latest_version", nullable = false)
    private int latestVersion;
    @Version
    private long lockVersion;

    public PolicyEntity(String policyCode) {
        this.policyCode = policyCode;
    }
}
