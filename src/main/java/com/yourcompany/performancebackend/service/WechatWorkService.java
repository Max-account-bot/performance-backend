package com.yourcompany.performancebackend.service;

import com.yourcompany.performancebackend.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;

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

    private String accessToken;
    private Instant tokenExpireTime;

    // 获取或刷新 access_token
    private String getAccessToken() {
        if (accessToken != null && tokenExpireTime != null && Instant.now().isBefore(tokenExpireTime)) {
            return accessToken;
        }
        try {
            String url = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=" + corpId + "&corpsecret=" + appSecret;
            Map resp = webClient.get().uri(url).retrieve().bodyToMono(Map.class).block();
            if (resp != null && resp.get("errcode") != null && Integer.parseInt(resp.get("errcode").toString()) == 0) {
                accessToken = resp.get("access_token").toString();
                int expiresIn = Integer.parseInt(resp.get("expires_in").toString());
                tokenExpireTime = Instant.now().plusSeconds(expiresIn - 60); // 提前60秒过期
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

    // 发送消息给指定用户
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
                    "url", "URL",
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

    // 通知审批人
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
        // 发送给所有审批人（这里用 @all 表示所有人，实际部署时替换为审批人的 userid）
        sendMessage("@all", title, desc);
    }

    // 通知申请人审批结果
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
}
