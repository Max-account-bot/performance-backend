package com.yourcompany.performancebackend.controller;

import com.yourcompany.performancebackend.model.*;
import com.yourcompany.performancebackend.repository.*;
import com.yourcompany.performancebackend.service.WechatWorkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PerformanceController {

    @Autowired private EmployeeRepository employeeRepo;
    @Autowired private PerformanceRepository performanceRepo;
    @Autowired private ApprovalRepository approvalRepo;
    @Autowired private WechatWorkService wechatService;

    // 接口1：员工提交绩效数据
    @PostMapping("/performance/submit")
    public Map<String, Object> submitPerformance(@RequestBody Map<String, Object> data) {
        Long employeeId = Long.valueOf(data.get("employeeId").toString());
        String month = (String) data.get("month");
        BigDecimal salesAmount = new BigDecimal(data.get("salesAmount").toString());
        Integer newCustomers = Integer.valueOf(data.get("newCustomers").toString());
        BigDecimal taskCompletion = data.containsKey("taskCompletion") && data.get("taskCompletion") != null
                ? new BigDecimal(data.get("taskCompletion").toString()) : BigDecimal.ZERO;
        String notes = (String) data.get("notes");

        Performance perf = new Performance();
        perf.setEmployeeId(employeeId);
        perf.setMonth(month);
        perf.setSalesAmount(salesAmount);
        perf.setNewCustomers(newCustomers);
        perf.setTaskCompletion(taskCompletion);
        perf.setNotes(notes);
        perf.setSubmittedAt(OffsetDateTime.now());
        perf = performanceRepo.save(perf);

        Approval approval = new Approval();
        approval.setPerformanceId(perf.getId());
        approval.setApplicantId(employeeId);
        approval.setStatus("pending");
        approvalRepo.save(approval);

        Employee applicant = employeeRepo.findById(employeeId).orElse(null);
        if (applicant != null) {
            wechatService.notifyApprover(applicant, perf);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "提交成功，已通知主管审批");
        return result;
    }

    // 接口2：获取某月的绩效汇总
    @GetMapping("/performance/summary")
    public List<Map<String, Object>> getSummary(@RequestParam String month) {
        List<Performance> list = performanceRepo.findByMonth(month);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Performance p : list) {
            Map<String, Object> item = new HashMap<>();
            Employee emp = employeeRepo.findById(p.getEmployeeId()).orElse(null);
            item.put("employeeName", emp != null ? emp.getName() : "未知");
            item.put("department", emp != null ? emp.getDepartment() : "未知");
            item.put("salesAmount", p.getSalesAmount());
            item.put("newCustomers", p.getNewCustomers());
            item.put("taskCompletion", p.getTaskCompletion());
            item.put("notes", p.getNotes());
            result.add(item);
        }
        return result;
    }

    // 接口3：审批操作
    @PostMapping("/approval/{id}/decide")
    public Map<String, Object> decideApproval(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String decision = body.get("decision");
        String comment = body.get("comment");

        Approval approval = approvalRepo.findById(id).orElse(null);
        if (approval == null) {
            return Map.of("success", false, "message", "审批记录不存在");
        }

        approval.setStatus(decision);
        approval.setComment(comment);
        approvalRepo.save(approval);

        wechatService.notifyApplicant(approval, decision);

        return Map.of("success", true, "message", "审批完成，已通知申请人");
    }

    // 接口4：获取待审批列表
    @GetMapping("/approval/pending")
    public List<Map<String, Object>> getPendingApprovals() {
        List<Approval> pending = approvalRepo.findByStatus("pending");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Approval a : pending) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", a.getId());
            item.put("performanceId", a.getPerformanceId());
            item.put("applicantId", a.getApplicantId());
            item.put("status", a.getStatus());
            item.put("createdAt", a.getCreatedAt());

            Employee applicant = employeeRepo.findById(a.getApplicantId()).orElse(null);
            item.put("applicantName", applicant != null ? applicant.getName() : "未知");
            item.put("applicantDept", applicant != null ? applicant.getDepartment() : "未知");

            Performance perf = performanceRepo.findById(a.getPerformanceId()).orElse(null);
            if (perf != null) {
                item.put("month", perf.getMonth());
                item.put("salesAmount", perf.getSalesAmount());
                item.put("newCustomers", perf.getNewCustomers());
                item.put("notes", perf.getNotes());
            }
            result.add(item);
        }
        return result;
    }

    // 接口5：获取所有员工列表（前端选择用）
    @GetMapping("/employees")
    public List<Employee> getAllEmployees() {
        return employeeRepo.findAll();
    }
}
