package com.example.authengine.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyVersionRepository extends JpaRepository<PolicyVersionEntity, Long> {
    Optional<PolicyVersionEntity> findByPolicyCodeAndVersionNumber(String code, int version);

    List<PolicyVersionEntity> findAllByPolicyCodeOrderByVersionNumberDesc(String code);
}
