package com.yourcompany.performancebackend.repository;

import com.yourcompany.performancebackend.model.Approval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {
    List<Approval> findByStatus(String status);
    List<Approval> findByApplicantId(Long applicantId);
    List<Approval> findByApproverId(Long approverId);
    List<Approval> findByApproverIdAndStatus(Long approverId, String status);
    List<Approval> findByApplicantIdAndStatus(Long applicantId, String status);
    @Query("SELECT a FROM Approval a WHERE a.ccTo LIKE %:userId%")
    List<Approval> findByCcToContaining(@Param("userId") String userId);
}
