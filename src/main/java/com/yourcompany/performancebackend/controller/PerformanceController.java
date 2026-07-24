package com.yourcompany.performancebackend.controller;

import com.yourcompany.performancebackend.model.*;
import com.yourcompany.performancebackend.repository.*;
import com.yourcompany.performancebackend.service.WechatWorkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class PerformanceController {

    private static final Logger log = LoggerFactory.getLogger(PerformanceController.class);

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
        if (data.containsKey("ccTo") && data.get("ccTo") != null) {
            approval.setCcTo(data.get("ccTo").toString());
        }
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
        // 记录审批人
        if (body.containsKey("approverId") && body.get("approverId") != null) {
            approval.setApproverId(Long.valueOf(body.get("approverId")));
        }
        // 记录抄送人
        if (body.containsKey("ccTo") && body.get("ccTo") != null) {
            approval.setCcTo(body.get("ccTo"));
        }
        approvalRepo.save(approval);

        wechatService.notifyApplicant(approval, decision);

        return Map.of("success", true, "message", "审批完成，已通知申请人");
    }

    // 接口4：获取待审批列表
    @GetMapping("/approval/pending")
    public List<Map<String, Object>> getPendingApprovals() {
        List<Approval> pending = approvalRepo.findByStatus("pending");
        return enrichApprovals(pending);
    }

    // 接口5：获取所有员工列表（前端选择用）
    @GetMapping("/employees")
    public List<Employee> getAllEmployees() {
        return employeeRepo.findAll();
    }

    // 接口6：从企业微信通讯录获取员工列表
    @GetMapping("/wechat/employees")
    public List<Map<String, String>> getWechatEmployees() {
        List<Map<String, String>> wechatEmployees = wechatService.fetchAllEmployees();
        if (wechatEmployees.isEmpty()) {
            log.warn("企业微信通讯录为空或获取失败，返回本地数据");
            // 回退到本地数据库
            List<Map<String, String>> localList = new ArrayList<>();
            for (Employee e : employeeRepo.findAll()) {
                Map<String, String> item = new HashMap<>();
                item.put("name", e.getName());
                item.put("department", e.getDepartment());
                item.put("userid", e.getWechatUserid() != null ? e.getWechatUserid() : "");
                item.put("position", e.getPosition() != null ? e.getPosition() : "");
                localList.add(item);
            }
            return localList;
        }
        return wechatEmployees;
    }

    // ========== 企业微信通讯录接口 ==========

    // 接口12：获取部门树
    @GetMapping("/departments")
    public List<Map<String, Object>> getDepartments() {
        return wechatService.fetchDepartments();
    }

    // 接口13：同步通讯录到本地数据库
    @PostMapping("/wechat/sync")
    public Map<String, Object> syncContacts() {
        Map<String, Object> result = wechatService.syncToDatabase();
        result.put("success", true);
        result.put("message", "通讯录同步完成");
        return result;
    }

    // ========== OAuth2.0 登录接口 ==========

    // 接口14：获取OAuth授权URL
    @GetMapping("/auth/login")
    public Map<String, String> getAuthUrl(@RequestParam String redirectUri) {
        String url = wechatService.getAuthUrl(redirectUri);
        return Map.of("authUrl", url);
    }

    // 接口15：OAuth回调（用code换用户信息）
    @GetMapping("/auth/callback")
    public Map<String, Object> authCallback(@RequestParam String code) {
        Map<String, Object> userInfo = wechatService.getUserInfoByCode(code);
        if (userInfo == null) {
            return Map.of("success", false, "message", "登录失败，无法获取用户信息");
        }
        userInfo.put("success", true);
        return userInfo;
    }

    // ========== MOM风格审批流程接口 ==========

    // 接口7：我的已办（我审批过的）
    @GetMapping("/approval/completed")
    public List<Map<String, Object>> getCompletedApprovals(@RequestParam Long userId) {
        List<Approval> approved = approvalRepo.findByApproverIdAndStatus(userId, "approved");
        List<Approval> rejected = approvalRepo.findByApproverIdAndStatus(userId, "rejected");
        List<Approval> list = new ArrayList<>(approved);
        list.addAll(rejected);
        // 按更新时间倒序
        list.sort((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()));
        return enrichApprovals(list);
    }

    // 接口8：我发起的
    @GetMapping("/approval/my-initiated")
    public List<Map<String, Object>> getMyInitiated(@RequestParam Long userId) {
        List<Approval> list = approvalRepo.findByApplicantId(userId);
        list.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return enrichApprovals(list);
    }

    // 接口9：抄送给我
    @GetMapping("/approval/cc")
    public List<Map<String, Object>> getCcToMe(@RequestParam String userId) {
        List<Approval> list = approvalRepo.findByCcToContaining(userId);
        list.sort((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()));
        return enrichApprovals(list);
    }

    // 接口10：驳回处理（我被驳回的，可以重新提交）
    @GetMapping("/approval/rejected")
    public List<Map<String, Object>> getRejected(@RequestParam Long userId) {
        List<Approval> list = approvalRepo.findByApplicantIdAndStatus(userId, "rejected");
        list.sort((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()));
        return enrichApprovals(list);
    }

    // 接口11：重新提交被驳回的绩效
    @PostMapping("/approval/{id}/resubmit")
    public Map<String, Object> resubmitRejected(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        Approval oldApproval = approvalRepo.findById(id).orElse(null);
        if (oldApproval == null) {
            return Map.of("success", false, "message", "审批记录不存在");
        }

        Performance oldPerf = performanceRepo.findById(oldApproval.getPerformanceId()).orElse(null);
        if (oldPerf == null) {
            return Map.of("success", false, "message", "绩效记录不存在");
        }

        // 创建新的绩效记录
        Performance newPerf = new Performance();
        newPerf.setEmployeeId(oldPerf.getEmployeeId());
        newPerf.setMonth(oldPerf.getMonth());
        newPerf.setSalesAmount(data.containsKey("salesAmount") ? new BigDecimal(data.get("salesAmount").toString()) : oldPerf.getSalesAmount());
        newPerf.setNewCustomers(data.containsKey("newCustomers") ? Integer.valueOf(data.get("newCustomers").toString()) : oldPerf.getNewCustomers());
        newPerf.setTaskCompletion(data.containsKey("taskCompletion") ? new BigDecimal(data.get("taskCompletion").toString()) : oldPerf.getTaskCompletion());
        newPerf.setNotes(data.containsKey("notes") ? (String) data.get("notes") : oldPerf.getNotes());
        newPerf.setSubmittedAt(OffsetDateTime.now());
        newPerf = performanceRepo.save(newPerf);

        // 旧的标记为已重新提交
        oldApproval.setStatus("resubmitted");
        approvalRepo.save(oldApproval);

        // 创建新的审批记录
        Approval newApproval = new Approval();
        newApproval.setPerformanceId(newPerf.getId());
        newApproval.setApplicantId(oldPerf.getEmployeeId());
        newApproval.setStatus("pending");
        approvalRepo.save(newApproval);

        Employee applicant = employeeRepo.findById(oldPerf.getEmployeeId()).orElse(null);
        if (applicant != null) {
            wechatService.notifyApprover(applicant, newPerf);
        }

        return Map.of("success", true, "message", "已重新提交审批");
    }

    // 通用方法：给审批记录填充员工和绩效信息
    private List<Map<String, Object>> enrichApprovals(List<Approval> approvals) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Approval a : approvals) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", a.getId());
            item.put("performanceId", a.getPerformanceId());
            item.put("applicantId", a.getApplicantId());
            item.put("approverId", a.getApproverId());
            item.put("status", a.getStatus());
            item.put("comment", a.getComment());
            item.put("ccTo", a.getCcTo());
            item.put("createdAt", a.getCreatedAt());
            item.put("updatedAt", a.getUpdatedAt());

            Employee applicant = employeeRepo.findById(a.getApplicantId()).orElse(null);
            item.put("applicantName", applicant != null ? applicant.getName() : "未知");
            item.put("applicantDept", applicant != null ? applicant.getDepartment() : "未知");

            Performance perf = performanceRepo.findById(a.getPerformanceId()).orElse(null);
            if (perf != null) {
                item.put("month", perf.getMonth());
                item.put("salesAmount", perf.getSalesAmount());
                item.put("newCustomers", perf.getNewCustomers());
                item.put("taskCompletion", perf.getTaskCompletion());
                item.put("notes", perf.getNotes());
            }
            result.add(item);
        }
        return result;
    }
}
