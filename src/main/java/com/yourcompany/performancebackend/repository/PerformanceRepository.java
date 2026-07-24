package com.yourcompany.performancebackend.repository;

import com.yourcompany.performancebackend.model.Performance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {
    List<Performance> findByMonth(String month);
    List<Performance> findByEmployeeId(Long employeeId);
}
