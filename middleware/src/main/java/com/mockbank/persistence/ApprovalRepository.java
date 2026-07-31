package com.mockbank.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {

    Optional<Approval> findByTransferRef(String transferRef);

    List<Approval> findByStatus(String status);
}
