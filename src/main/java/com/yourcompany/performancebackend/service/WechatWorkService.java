package com.yourcompany.performancebackend.service;

import com.yourcompany.performancebackend.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WechatWorkService {

    private static final Logger log = LoggerFactory.getLogger(WechatWorkService.class);

    // 通知审批人
    public void notifyApprover(Employee applicant, Performance perf) {
        log.info("========================================");
        log.info("  【企业微信推送 - 审批通知】");
        log.info("  员工：{}", applicant.getName());
        log.info("  部门：{}", applicant.getDepartment());
        log.info("  月份：{}", perf.getMonth());
        log.info("  业绩金额：{} 万元", perf.getSalesAmount());
        log.info("  新客户数：{}", perf.getNewCustomers());
        log.info("  备注：{}", perf.getNotes());
        log.info("  -> 已推送给审批人（模拟模式）");
        log.info("========================================");
    }

    // 通知申请人审批结果
    public void notifyApplicant(Approval approval, String decision) {
        String result = "approved".equals(decision) ? "已通过 ✅" : "已拒绝 ❌";
        log.info("========================================");
        log.info("  【企业微信推送 - 审批结果】");
        log.info("  审批编号：#{}", approval.getId());
        log.info("  审批结果：{}", result);
        log.info("  审批意见：{}", approval.getComment() != null ? approval.getComment() : "无");
        log.info("  -> 已推送给申请人（模拟模式）");
        log.info("========================================");
    }
}
