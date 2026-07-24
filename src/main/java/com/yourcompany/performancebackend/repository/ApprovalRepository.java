package com.yourcompany.performancebackend.repository;

import com.yourcompany.performancebackend.model.Approval;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {
    List<Approval> findByStatus(String status);
    List<Approval> findByApplicantId(Long applicantId);
}
