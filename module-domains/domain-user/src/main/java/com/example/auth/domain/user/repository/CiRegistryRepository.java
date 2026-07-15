package com.example.auth.domain.user.repository;

import com.example.auth.domain.user.entity.CiRegistry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CiRegistryRepository extends JpaRepository<CiRegistry, UUID> {

    Optional<CiRegistry> findByCiHash(String ciHash);
}
