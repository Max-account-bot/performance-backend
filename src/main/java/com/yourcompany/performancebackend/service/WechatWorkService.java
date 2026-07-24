package com.yourcompany.performancebackend.service;

import com.yourcompany.performancebackend.model.*;
import com.yourcompany.performancebackend.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.*;

@Service
public class WechatWorkService {

    private static final Logger log = LoggerFactory.getLogger(WechatWorkService.class);
    private final WebClient webClient = WebClient.create();

    @Value("${wechat.corp-id}")
    private String corpId;

    @Value("${wechat.app-secret}")
    private String appSecret;

    @Value("${wechat.agent-id}")
    private String agentId;

    @Autowired
    private EmployeeRepository employeeRepo;

    private String accessToken;
    private Instant tokenExpireTime;
    private final Map<Integer, String> departmentNames = new HashMap<>();

    // ========== Token 管理 ==========

    public String getAccessToken() {
        if (accessToken != null && tokenExpireTime != null && Instant.now().isBefore(tokenExpireTime)) {
            return accessToken;
        }
        try {
            String url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=" + corpId + "&corpsecret=" + appSecret;
            Map resp = webClient.get().uri(url).retrieve().bodyToMono(Map.class).block();
            if (resp != null && resp.get("errcode") != null && Integer.parseInt(resp.get("errcode").toString()) == 0) {
                accessToken = resp.get("access_token").toString();
                int expiresIn = Integer.parseInt(resp.get("expires_in").toString());
                tokenExpireTime = Instant.now().plusSeconds(expiresIn - 60);
                log.info("企业微信 access_token 获取成功");
                return accessToken;
            } else {
                String errMsg = resp != null ? resp.get("errmsg").toString() : "无响应";
                log.warn("企业微信 access_token 获取失败: {}", errMsg);
                return null;
            }
        } catch (Exception e) {
            log.warn("企业微信 access_token 获取异常: {}", e.getMessage());
            return null;
        }
    }

    // ========== 通讯录 - 部门树 ==========

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchDepartments() {
        String token = getAccessToken();
        if (token == null) return new ArrayList<>();
        try {
            String url = "https://qyapi.weixin.qq.com/cgi-bin/department/list?access_token=" + token;
            Map resp = webClient.get().uri(url).retrieve().bodyToMono(Map.class).block();
            if (resp != null && Integer.parseInt(resp.get("errcode").toString()) == 0) {
                List<Map<String, Object>> depts = (List<Map<String, Object>>) resp.get("department");
                if (depts == null) return new ArrayList<>();

                // 缓存部门名称
                for (Map<String, Object> dept : depts) {
                    departmentNames.put(
                        Integer.parseInt(dept.get("id").toString()),
                        dept.get("name").toString()
                    );
                }

                List<Map<String, Object>> result = new ArrayList<>();
                for (Map<String, Object> dept : depts) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", Integer.parseInt(dept.get("id").toString()));
                    item.put("name", dept.get("name"));
                    item.put("parentId", Integer.parseInt(dept.get("parentid").toString()));
                    item.put("order", dept.get("order"));

                    List<String> leaders = (List<String>) dept.get("department_leader");
                    item.put("leaders", leaders != null ? leaders : new ArrayList<>());

                    // 解析负责人姓名
                    List<Map<String, String>> leaderDetails = new ArrayList<>();
                    if (leaders != null) {
                        for (String userId : leaders) {
                            Map<String, String> leaderInfo = new HashMap<>();
                            leaderInfo.put("userid", userId);
                            Map detail = getUserDetail(token, userId);
                            leaderInfo.put("name", detail != null ? detail.get("name").toString() : userId);
                            leaderDetails.add(leaderInfo);
                        }
                    }
                    item.put("leaderDetails", leaderDetails);
                    result.add(item);
                }

                log.info("获取到 {} 个部门", result.size());
                return result;
            }
        } catch (Exception e) {
            log.warn("获取部门列表异常: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    // ========== 通讯录 - 全量员工 ==========

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> fetchAllEmployees() {
        List<Map<String, String>> result = new ArrayList<>();
        String token = getAccessToken();
        if (token == null) {
            log.warn("无法获取企业微信通讯录（access_token 为空）");
            return result;
        }

        try {
            // Step 1: 获取所有部门
            String deptUrl = "https://qyapi.weixin.qq.com/cgi-bin/department/list?access_token=" + token;
            Map deptResp = webClient.get().uri(deptUrl).retrieve().bodyToMono(Map.class).block();
            if (deptResp == null || Integer.parseInt(deptResp.get("errcode").toString()) != 0) {
                log.warn("获取部门列表失败: {}", deptResp);
                return result;
            }

            List<Map<String, Object>> depts = (List<Map<String, Object>>) deptResp.get("department");
            if (depts == null || depts.isEmpty()) return result;

            // 缓存部门名称
            for (Map<String, Object> dept : depts) {
                departmentNames.put(
                    Integer.parseInt(dept.get("id").toString()),
                    dept.get("name").toString()
                );
            }

            // Step 2: 逐部门拉取成员（使用 user/list，每个部门单独请求）
            Set<String> addedUserIds = new HashSet<>();

            for (Map<String, Object> dept : depts) {
                int deptId = Integer.parseInt(dept.get("id").toString());
                String deptName = dept.get("name").toString();

                String userUrl = "https://qyapi.weixin.qq.com/cgi-bin/user/list?access_token="
                    + token + "&department_id=" + deptId;
                Map userResp = webClient.get().uri(userUrl).retrieve().bodyToMono(Map.class).block();

                if (userResp != null && Integer.parseInt(userResp.get("errcode").toString()) == 0) {
                    List<Map<String, Object>> users = (List<Map<String, Object>>) userResp.get("userlist");
                    if (users != null) {
                        for (Map<String, Object> user : users) {
                            String userid = user.get("userid").toString();
                            if (addedUserIds.contains(userid)) continue;
                            addedUserIds.add(userid);

                            Map<String, String> emp = new HashMap<>();
                            emp.put("name", user.get("name").toString());
                            emp.put("userid", userid);

                            // 使用主部门名称
                            List<Integer> userDeptIds = (List<Integer>) user.get("department");
                            int mainDept = user.get("main_department") != null
                                ? Integer.parseInt(user.get("main_department").toString())
                                : (userDeptIds != null && !userDeptIds.isEmpty() ? userDeptIds.get(0) : 0);
                            emp.put("department", departmentNames.getOrDefault(mainDept, deptName));
                            emp.put("position", user.get("position") != null ? user.get("position").toString() : "");
                            emp.put("phone", user.get("mobile") != null ? user.get("mobile").toString() : "");
                            emp.put("status", user.get("status") != null ? user.get("status").toString() : "1");

                            result.add(emp);
                        }
                    }
                }
            }

            log.info("企业微信员工总数: {}", result.size());
        } catch (Exception e) {
            log.warn("获取企业微信通讯录异常: {}", e.getMessage());
        }
        return result;
    }

    // ========== 通讯录 - 同步到本地数据库 ==========

    public Map<String, Object> syncToDatabase() {
        List<Map<String, String>> employees = fetchAllEmployees();
        int added = 0, updated = 0;

        for (Map<String, String> emp : employees) {
            String userid = emp.get("userid");
            Optional<Employee> existing = employeeRepo.findByWechatUserid(userid);

            if (existing.isPresent()) {
                Employee e = existing.get();
                e.setName(emp.get("name"));
                e.setDepartment(emp.get("department"));
                e.setPosition(emp.get("position"));
                e.setPhone(emp.get("phone"));
                employeeRepo.save(e);
                updated++;
            } else {
                Employee e = new Employee();
                e.setName(emp.get("name"));
                e.setDepartment(emp.get("department"));
                e.setPosition(emp.get("position"));
                e.setPhone(emp.get("phone"));
                e.setWechatUserid(userid);
                employeeRepo.save(e);
                added++;
            }
        }

        log.info("通讯录同步完成: 新增 {} 人, 更新 {} 人", added, updated);
        Map<String, Object> result = new HashMap<>();
        result.put("added", added);
        result.put("updated", updated);
        result.put("total", employees.size());
        return result;
    }

    // ========== OAuth2.0 登录 ==========

    public String getAuthUrl(String redirectUri) {
        return "https://open.weixin.qq.com/connect/oauth2/authorize"
            + "?appid=" + corpId
            + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8)
            + "&response_type=code"
            + "&scope=snsapi_base"
            + "&state=STATE"
            + "&agentid=" + agentId
            + "#wechat_redirect";
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getUserInfoByCode(String code) {
        String token = getAccessToken();
        if (token == null) return null;
        try {
            // Step 1: code 换 userid
            String url = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token="
                + token + "&code=" + code;
            Map resp = webClient.get().uri(url).retrieve().bodyToMono(Map.class).block();
            if (resp == null || Integer.parseInt(resp.get("errcode").toString()) != 0) {
                log.warn("OAuth getuserinfo 失败: {}", resp);
                return null;
            }

            String userId = resp.get("userid") != null ? resp.get("userid").toString() : null;
            if (userId == null || userId.isEmpty()) {
                log.warn("OAuth 未获取到 userid, 可能非企业成员");
                return null;
            }

            // Step 2: userid 换详情
            Map detail = getUserDetail(token, userId);
            if (detail == null) return null;

            Map<String, Object> result = new HashMap<>();
            result.put("userid", userId);
            result.put("name", detail.get("name"));
            result.put("department", detail.get("department"));
            result.put("main_department", detail.get("main_department"));
            result.put("position", detail.get("position"));
            result.put("mobile", detail.get("telephone"));

            // 解析主部门名称
            int mainDept = detail.get("main_department") != null
                ? Integer.parseInt(detail.get("main_department").toString()) : 0;
            result.put("departmentName", departmentNames.getOrDefault(mainDept, ""));

            // 查找本地员工 ID
            Optional<Employee> localEmp = employeeRepo.findByWechatUserid(userId);
            if (localEmp.isPresent()) {
                result.put("employeeId", localEmp.get().getId());
            }

            return result;
        } catch (Exception e) {
            log.warn("OAuth 获取用户信息异常: {}", e.getMessage());
            return null;
        }
    }

    // ========== 消息推送 ==========

    public void notifyApprover(Employee applicant, Performance perf) {
        log.info("========================================");
        log.info("  【企业微信推送 - 审批通知】");
        log.info("  员工：{} | 部门：{} | 月份：{}", applicant.getName(), applicant.getDepartment(), perf.getMonth());
        log.info("  业绩金额：{} 万元 | 新客户数：{}", perf.getSalesAmount(), perf.getNewCustomers());
        log.info("========================================");

        String title = "绩效审批通知";
        String desc = String.format(
            "<div>员工：%s（%s）</div><div>月份：%s</div><div>业绩金额：%s 万元</div><div>新客户数：%d</div><div>请尽快审批</div>",
            applicant.getName(), applicant.getDepartment(),
            perf.getMonth(), perf.getSalesAmount(), perf.getNewCustomers()
        );
        sendMessage("@all", title, desc);
    }

    public void notifyApplicant(Approval approval, String decision) {
        String result = "approved".equals(decision) ? "已通过" : "已拒绝";
        log.info("========================================");
        log.info("  【企业微信推送 - 审批结果】");
        log.info("  审批编号：#{} | 结果：{}", approval.getId(), result);
        log.info("========================================");

        String title = "绩效审批结果";
        String desc = String.format(
            "<div>审批编号：#%d</div><div>结果：%s</div><div>意见：%s</div>",
            approval.getId(), result,
            approval.getComment() != null ? approval.getComment() : "无"
        );
        sendMessage("@all", title, desc);
    }

    // ========== 内部工具方法 ==========

    @SuppressWarnings("unchecked")
    private Map getUserDetail(String token, String userId) {
        try {
            String url = "https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token=" + token + "&userid=" + userId;
            Map resp = webClient.get().uri(url).retrieve().bodyToMono(Map.class).block();
            if (resp != null && Integer.parseInt(resp.get("errcode").toString()) == 0) {
                return resp;
            }
        } catch (Exception e) {
            log.warn("获取用户详情失败 ({}): {}", userId, e.getMessage());
        }
        return null;
    }

    private String getDepartmentName(String token, int deptId) {
        String cached = departmentNames.get(deptId);
        if (cached != null) return cached;
        try {
            String url = "https://qyapi.weixin.qq.com/cgi-bin/department/get?access_token=" + token + "&id=" + deptId;
            Map resp = webClient.get().uri(url).retrieve().bodyToMono(Map.class).block();
            if (resp != null && Integer.parseInt(resp.get("errcode").toString()) == 0) {
                Map<String, Object> dept = (Map<String, Object>) resp.get("department");
                if (dept != null) {
                    String name = dept.get("name").toString();
                    departmentNames.put(deptId, name);
                    return name;
                }
            }
        } catch (Exception e) {
            log.warn("获取部门名称失败: {}", e.getMessage());
        }
        return "部门" + deptId;
    }

    private void sendMessage(String toUser, String title, String description) {
        String token = getAccessToken();
        if (token == null) {
            log.warn("无法发送企业微信消息（access_token 为空），仅记录日志");
            log.info("【消息内容】发给: {} | 标题: {} | 内容: {}", toUser, title, description);
            return;
        }
        try {
            String url = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + token;
            Map<String, Object> body = Map.of(
                "touser", toUser,
                "msgtype", "textcard",
                "agentid", Integer.parseInt(agentId),
                "textcard", Map.of(
                    "title", title,
                    "description", description,
                    "url", "https://perf.nexteerly.com",
                    "btntxt", "查看详情"
                )
            );
            Map resp = webClient.post().uri(url)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class).block();
            if (resp != null && resp.get("errcode") != null && Integer.parseInt(resp.get("errcode").toString()) == 0) {
                log.info("企业微信消息发送成功 -> {}", toUser);
            } else {
                log.warn("企业微信消息发送失败: {}", resp);
            }
        } catch (Exception e) {
            log.warn("企业微信消息发送异常: {}", e.getMessage());
        }
    }
}
