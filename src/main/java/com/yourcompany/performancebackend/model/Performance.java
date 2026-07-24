package com.yourcompany.performancebackend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "performance")
public class Performance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "perf_month")
    private String month;

    @Column(name = "sales_amount")
    private BigDecimal salesAmount;

    @Column(name = "new_customers")
    private Integer newCustomers;

    @Column(name = "task_completion")
    private BigDecimal taskCompletion;

    private String notes;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    public Performance() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public BigDecimal getSalesAmount() { return salesAmount; }
    public void setSalesAmount(BigDecimal salesAmount) { this.salesAmount = salesAmount; }
    public Integer getNewCustomers() { return newCustomers; }
    public void setNewCustomers(Integer newCustomers) { this.newCustomers = newCustomers; }
    public BigDecimal getTaskCompletion() { return taskCompletion; }
    public void setTaskCompletion(BigDecimal taskCompletion) { this.taskCompletion = taskCompletion; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(OffsetDateTime submittedAt) { this.submittedAt = submittedAt; }
}
