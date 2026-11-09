package com.example.authengine.policy;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PolicyRepository extends JpaRepository<PolicyEntity, Long> {
    Optional<PolicyEntity> findByPolicyCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PolicyEntity p where p.policyCode = :code")
    Optional<PolicyEntity> lockByCode(@Param("code") String code);
}
