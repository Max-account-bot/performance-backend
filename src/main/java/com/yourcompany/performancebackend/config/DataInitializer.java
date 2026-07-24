package com.yourcompany.performancebackend.config;

import com.yourcompany.performancebackend.model.*;
import com.yourcompany.performancebackend.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initData(EmployeeRepository employeeRepo,
                               PerformanceRepository performanceRepo,
                               ApprovalRepository approvalRepo) {
        return args -> {
            // 只在员工表为空时插入示例数据
            if (employeeRepo.count() > 0) {
                System.out.println("数据库已有数据，跳过初始化。");
                return;
            }

            // Sample employees
            Employee e1 = new Employee("张伟", "销售部", "销售经理");
            e1.setWechatUserid("zhangwei");
            e1.setPhone("13800001111");
            employeeRepo.save(e1);

            Employee e2 = new Employee("李娜", "市场部", "市场专员");
            e2.setWechatUserid("lina");
            e2.setPhone("13800002222");
            employeeRepo.save(e2);

            Employee e3 = new Employee("王强", "技术部", "技术主管");
            e3.setWechatUserid("wangqiang");
            e3.setPhone("13800003333");
            employeeRepo.save(e3);

            Employee e4 = new Employee("赵敏", "销售部", "销售代表");
            e4.setWechatUserid("zhaomin");
            e4.setPhone("13800004444");
            employeeRepo.save(e4);

            Employee e5 = new Employee("陈丽", "人事部", "人事经理");
            e5.setWechatUserid("chenli");
            e5.setPhone("13800005555");
            employeeRepo.save(e5);

            // Sample performance data for current month
            Performance p1 = new Performance();
            p1.setEmployeeId(e1.getId());
            p1.setMonth("2026-07");
            p1.setSalesAmount(new BigDecimal("128500.00"));
            p1.setNewCustomers(15);
            p1.setTaskCompletion(new BigDecimal("95.5"));
            p1.setNotes("超额完成季度目标");
            p1.setSubmittedAt(OffsetDateTime.now());
            performanceRepo.save(p1);

            Performance p2 = new Performance();
            p2.setEmployeeId(e2.getId());
            p2.setMonth("2026-07");
            p2.setSalesAmount(new BigDecimal("96200.00"));
            p2.setNewCustomers(8);
            p2.setTaskCompletion(new BigDecimal("88.0"));
            p2.setNotes("成功 launching 新营销活动");
            p2.setSubmittedAt(OffsetDateTime.now());
            performanceRepo.save(p2);

            Performance p3 = new Performance();
            p3.setEmployeeId(e3.getId());
            p3.setMonth("2026-07");
            p3.setSalesAmount(new BigDecimal("84300.00"));
            p3.setNewCustomers(5);
            p3.setTaskCompletion(new BigDecimal("92.0"));
            p3.setNotes("按时交付3个项目");
            p3.setSubmittedAt(OffsetDateTime.now());
            performanceRepo.save(p3);

            Performance p4 = new Performance();
            p4.setEmployeeId(e4.getId());
            p4.setMonth("2026-07");
            p4.setSalesAmount(new BigDecimal("75800.00"));
            p4.setNewCustomers(12);
            p4.setTaskCompletion(new BigDecimal("85.0"));
            p4.setNotes("客户留存率良好");
            p4.setSubmittedAt(OffsetDateTime.now());
            performanceRepo.save(p4);

            // Sample pending approvals
            Approval a1 = new Approval();
            a1.setPerformanceId(p1.getId());
            a1.setApplicantId(e1.getId());
            a1.setApproverId(e5.getId());
            a1.setStatus("pending");
            approvalRepo.save(a1);

            Approval a2 = new Approval();
            a2.setPerformanceId(p2.getId());
            a2.setApplicantId(e2.getId());
            a2.setApproverId(e5.getId());
            a2.setStatus("pending");
            approvalRepo.save(a2);

            System.out.println("========================================");
            System.out.println("  示例数据加载成功！");
            System.out.println("  5名员工、4条绩效记录、2条待审批");
            System.out.println("  数据库控制台: http://localhost:8080/h2-console");
            System.out.println("========================================");
        };
    }
}
