package com.example.authengine.integration;

import com.example.authengine.domain.FactorType;
import com.example.authengine.domain.PolicyNode;
import com.example.authengine.policy.PolicyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoginFlowIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    PolicyService policies;

    private PolicyNode factor(String id, FactorType type) {
        return new PolicyNode(PolicyNode.Op.FACTOR, id, type, null, null);
    }

    private PolicyNode any(PolicyNode... children) {
        return new PolicyNode(PolicyNode.Op.ANY, null, null, null, List.of(children));
    }

    private PolicyNode all(PolicyNode... children) {
        return new PolicyNode(PolicyNode.Op.ALL, null, null, null, List.of(children));
    }

    private String extractCookie(MockHttpServletResponse response) {
        String setCookie = response.getHeader("Set-Cookie");
        assertNotNull(setCookie);
        return setCookie.split(";")[0].split("=", 2)[1];
    }

    private Started start() throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/login/attempts")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        JsonNode state = json.readTree(response.getContentAsString());
        String cookie = extractCookie(response);
        return new Started(state.get("attemptId").asText(), cookie, state.get("policyVersion").asInt());
    }

    @Test
    void dynamicPublishSnapshotReplayProtectionAndQrApproval() throws Exception {
        // V2 是仅密码可完成的策略：旧登录流程之后仍然使用此快照。
        int v2 = policies.saveDraft("WEB_LOGIN", factor("pwd", FactorType.PASSWORD)).version();
        policies.publish("WEB_LOGIN", v2);
        Started old = start();
        assertEquals(v2, old.policyVersion());

        // V3 要求密码 + 短信；发布不会改变已开始的旧流程。
        int v3 = policies.saveDraft("WEB_LOGIN", all(factor("pwd", FactorType.PASSWORD),
                factor("sms", FactorType.SMS_OTP))).version();
        policies.publish("WEB_LOGIN", v3);
        Started newer = start();
        assertEquals(v3, newer.policyVersion());

        mvc.perform(post("/api/login/attempts/{id}/factors/pwd/verify", old.id())
                        .cookie(new Cookie("AUTH_BIND", old.cookie())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"Demo@123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attempt.canComplete").value(true));
        mvc.perform(post("/api/login/attempts/{id}/factors/pwd/verify", newer.id())
                        .cookie(new Cookie("AUTH_BIND", newer.cookie())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credential\":\"Demo@123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attempt.canComplete").value(false));

        MockHttpServletResponse completed = mvc.perform(post("/api/login/attempts/{id}/complete", old.id())
                        .cookie(new Cookie("AUTH_BIND", old.cookie())))
                .andExpect(status().isOk()).andReturn().getResponse();
        String bearer = json.readTree(completed.getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"));
        mvc.perform(post("/api/login/attempts/{id}/complete", old.id())
                .cookie(new Cookie("AUTH_BIND", old.cookie()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/login/attempts/{id}", newer.id())
                        .cookie(new Cookie("AUTH_BIND", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")))
                .andExpect(status().isForbidden());

        // V4：扫码 OR 密码，扫码 APP 确认可以推进流程，网页不能自行提交“扫码已完成”。
        int v4 = policies.saveDraft("WEB_LOGIN", any(factor("qr", FactorType.APP_QR_APPROVAL),
                factor("pwd", FactorType.PASSWORD))).version();
        policies.publish("WEB_LOGIN", v4);
        Started qrAttempt = start();
        MockHttpServletResponse startedQr = mvc.perform(
                        post("/api/login/attempts/{id}/factors/qr/start", qrAttempt.id())
                                .cookie(new Cookie("AUTH_BIND", qrAttempt.cookie())))
                .andExpect(status().isOk()).andReturn().getResponse();
        String ticket = json.readTree(startedQr.getContentAsString()).get("qrTicket").asText();
        mvc.perform(post("/api/app/qr/approve")
                        .with(httpBasic("alice", "Demo@123456"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("ticket", ticket))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canComplete").value(true));
        mvc.perform(post("/api/login/attempts/{id}/complete", qrAttempt.id())
                .cookie(new Cookie("AUTH_BIND", qrAttempt.cookie()))).andExpect(status().isOk());
        mvc.perform(post("/api/app/qr/approve")
                        .with(httpBasic("alice", "Demo@123456"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("ticket", ticket))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/policies/WEB_LOGIN/active"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testQrApproveWithMockTicket() throws Exception {
        // 1. 创建真实 attempt
        MockHttpServletResponse startRes = mvc.perform(post("/api/login/attempts")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        JsonNode startNode = json.readTree(startRes.getContentAsString());
        String attemptId = startNode.get("attemptId").asText();
        String cookie = extractCookie(startRes);

        // 2. 启动 qr 因子
        MockHttpServletResponse qrRes = mvc.perform(post("/api/login/attempts/{id}/factors/qr/start", attemptId)
                        .cookie(new Cookie("AUTH_BIND", cookie)))
                .andExpect(status().isOk()).andReturn().getResponse();
        String realTicket = json.readTree(qrRes.getContentAsString()).get("qrTicket").asText();

        // 3. 测试使用 alice (正确用户) approve realTicket
        mvc.perform(post("/api/app/qr/approve")
                        .with(httpBasic("alice", "Demo@123456"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"" + realTicket + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.canComplete").value(true));

        // 4. 测试再次提交同一个 realTicket (防重放 / 已失效)
        mvc.perform(post("/api/app/qr/approve")
                        .with(httpBasic("alice", "Demo@123456"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"" + realTicket + "\"}"))
                .andExpect(status().isForbidden());

        // 5. 测试使用 admin approve alice 的 realTicket
        // 重新为一个新的 attempt 发起 qr
        MockHttpServletResponse startRes2 = mvc.perform(post("/api/login/attempts")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"alice\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();
        String attemptId2 = json.readTree(startRes2.getContentAsString()).get("attemptId").asText();
        String cookie2 = extractCookie(startRes2);
        MockHttpServletResponse qrRes2 = mvc.perform(post("/api/login/attempts/{id}/factors/qr/start", attemptId2)
                        .cookie(new Cookie("AUTH_BIND", cookie2)))
                .andExpect(status().isOk()).andReturn().getResponse();
        String realTicket2 = json.readTree(qrRes2.getContentAsString()).get("qrTicket").asText();

        mvc.perform(post("/api/app/qr/approve")
                        .with(httpBasic("admin", "Admin@123456"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"" + realTicket2 + "\"}"))
                .andExpect(status().isForbidden());

        // 6. 测试无认证且未传 approvedBy 时调用
        mvc.perform(post("/api/app/qr/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"_pF8vJea3KAcBoLthGgcCohdzLXf594P.Se-qXDly9a0o9FNm-31YU-BZ_uW1ZQJDPQzRz6TmGGY\"}"))
                .andExpect(status().isUnauthorized())
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print());

        // 7. 测试未传 Basic Auth 但在请求体传入 approvedBy 备选参数
        mvc.perform(post("/api/app/qr/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"_pF8vJea3KAcBoLthGgcCohdzLXf594P.Se-qXDly9a0o9FNm-31YU-BZ_uW1ZQJDPQzRz6TmGGY\", \"approvedBy\":\"alice\"}"))
                .andExpect(status().isNotFound()) // ticket 不存在因此预期返回 404 而不是 500
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print());

        // 8. 测试 text/plain 媒体类型直接传入 JSON 字符串 (兼容 Postman text 格式)
        mvc.perform(post("/api/app/qr/approve")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"ticket\":\"_pF8vJea3KAcBoLthGgcCohdzLXf594P.Se-qXDly9a0o9FNm-31YU-BZ_uW1ZQJDPQzRz6TmGGY\", \"approvedBy\":\"alice\"}"))
                .andExpect(status().isNotFound())
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print());

        // 9. 测试 text/plain 直接传入纯文本 Ticket 密文 (无 JSON 包装)
        mvc.perform(post("/api/app/qr/approve")
                        .contentType(MediaType.TEXT_PLAIN)
                        .param("approvedBy", "alice")
                        .content("_pF8vJea3KAcBoLthGgcCohdzLXf594P.Se-qXDly9a0o9FNm-31YU-BZ_uW1ZQJDPQzRz6TmGGY"))
                .andExpect(status().isNotFound())
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print());

        // 10. 测试仅通过 Query 参数调用
        mvc.perform(post("/api/app/qr/approve")
                        .param("ticket", "_pF8vJea3KAcBoLthGgcCohdzLXf594P.Se-qXDly9a0o9FNm-31YU-BZ_uW1ZQJDPQzRz6TmGGY")
                        .param("approvedBy", "alice"))
                .andExpect(status().isNotFound())
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print());
    }

    private record Started(String id, String cookie, int policyVersion) {
    }
}
