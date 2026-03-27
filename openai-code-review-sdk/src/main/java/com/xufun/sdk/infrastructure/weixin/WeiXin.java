package com.xufun.sdk.infrastructure.weixin;

import com.alibaba.fastjson2.JSON;
import com.xufun.sdk.infrastructure.weixin.dto.TemplateMessageDTO;
import com.xufun.sdk.utils.WXAccessTokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class WeiXin {
    private final Logger logger = LoggerFactory.getLogger(WeiXin.class);

    private final String appid;

    private final String secret;

    private final String touser;

    private final String template_id;

    public WeiXin(String appid, String secret, String touser, String template_id) {
        this.appid = appid;
        this.secret = secret;
        this.touser = touser;
        this.template_id = template_id;
    }

    public void sendTemplateMessage(String logUrl, Map<String, Map<String, String>> data) throws Exception {
        logger.info("start sending wechat template message...");
        logger.info("logUrl: {}", logUrl);
        logger.info("touser: {}, template_id: {}", touser, template_id);
        
        //获得用于操作公众号的令牌
        logger.info("getting access token...");
        String accessToken = WXAccessTokenUtils.getAccessToken(appid, secret);
        if (accessToken == null || accessToken.isEmpty()) {
            throw new RuntimeException("failed to get access token");
        }
        logger.info("access token obtained: {}", accessToken.substring(0, 10) + "...");
        
        //创建模板消息
        String normalizedToUser = touser == null ? null : touser.trim();
        String normalizedTemplateId = template_id == null ? null : template_id.trim();
        if (normalizedToUser == null || normalizedToUser.isEmpty()) {
            throw new RuntimeException("WEIXIN_TOUSER is empty, please configure a valid openid");
        }
        if (normalizedTemplateId == null || normalizedTemplateId.isEmpty()) {
            throw new RuntimeException("WEIXIN_TEMPLATE_ID is empty, please configure a valid template id");
        }

        TemplateMessageDTO templateMessageDTO = new TemplateMessageDTO(
                normalizedToUser,
                normalizedTemplateId,
                logUrl,
                data
        );
        logger.info("template message created");
        
        //创建请求
        URL url = new URL(String.format("https://api.weixin.qq.com/cgi-bin/message/template/send?access_token=%s", accessToken));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        
        //发送请求
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = JSON.toJSONString(templateMessageDTO).getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
            os.flush();
        }
        logger.info("request sent to wechat server");
        
        //接收结果
        int responseCode = conn.getResponseCode();
        logger.info("wechat response code: {}", responseCode);
        
        String response = "unknown";
        try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name())) {
            response = scanner.useDelimiter("\\A").next();
        } catch (Exception e) {
            logger.warn("failed to read response body", e);
        }
        
        logger.info("openai-code-review weixin template message response: {}", response);
        
        if (responseCode != 200) {
            throw new RuntimeException("wechat API returned error response: " + responseCode);
        }

        WechatSendResponse sendResponse = JSON.parseObject(response, WechatSendResponse.class);
        if (sendResponse == null) {
            throw new RuntimeException("wechat API returned invalid response body");
        }
        if (sendResponse.getErrcode() != null && sendResponse.getErrcode() != 0) {
            if (sendResponse.getErrcode() == 40003) {
                logAvailableOpenIdHints(accessToken, normalizedToUser);
            }
            throw new RuntimeException("wechat template message send failed, errcode: "
                    + sendResponse.getErrcode() + ", errmsg: " + sendResponse.getErrmsg());
        }
    }

    private void logAvailableOpenIdHints(String accessToken, String configuredToUser) {
        try {
            List<String> openIds = queryFollowerOpenIds(accessToken);
            if (openIds.isEmpty()) {
                logger.warn("openid diagnosis: no followers found for current appid");
                return;
            }

            logger.warn("openid diagnosis: found {} follower openid(s). Configure WEIXIN_TOUSER using one of them.", openIds.size());
            logConfiguredToUserDiagnostics(configuredToUser, openIds);
            int maxLogCount = Math.min(openIds.size(), 20);
            for (int i = 0; i < maxLogCount; i++) {
                String openId = openIds.get(i);
                String nickname = queryNickname(accessToken, openId);
                logger.warn("openid candidate #{}: openid={}, nickname={}", i + 1, openId, nickname == null ? "unknown" : nickname);
            }
        } catch (Exception e) {
            logger.warn("openid diagnosis failed", e);
        }
    }

    private void logConfiguredToUserDiagnostics(String configuredToUser, List<String> candidates) {
        if (configuredToUser == null) {
            logger.warn("openid diagnosis: configured WEIXIN_TOUSER is null");
            return;
        }

        String stripped = stripWrappingQuotes(configuredToUser);
        boolean exactMatch = candidates.contains(configuredToUser);
        boolean strippedMatch = candidates.contains(stripped);
        logger.warn("openid diagnosis: configured WEIXIN_TOUSER length={}, exactMatch={}, strippedQuoteMatch={}",
                configuredToUser.length(), exactMatch, strippedMatch);

        if (configuredToUser.length() > 0) {
            int firstCodePoint = configuredToUser.codePointAt(0);
            int lastCodePoint = configuredToUser.codePointBefore(configuredToUser.length());
            logger.warn("openid diagnosis: configured WEIXIN_TOUSER firstCodePoint=U+{}, lastCodePoint=U+{}",
                    Integer.toHexString(firstCodePoint).toUpperCase(),
                    Integer.toHexString(lastCodePoint).toUpperCase());
        }

        if (!configuredToUser.equals(stripped)) {
            logger.warn("openid diagnosis: configured WEIXIN_TOUSER wrapped quotes removed, strippedValue={}", stripped);
        }
    }

    private String stripWrappingQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private List<String> queryFollowerOpenIds(String accessToken) throws Exception {
        URL followersUrl = new URL(String.format("https://api.weixin.qq.com/cgi-bin/user/get?access_token=%s", accessToken));
        HttpURLConnection conn = (HttpURLConnection) followersUrl.openConnection();
        conn.setRequestMethod("GET");

        String body = readResponseBody(conn);
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("query follower openid failed, responseCode: " + responseCode + ", body: " + body);
        }

        FollowerListResponse response = JSON.parseObject(body, FollowerListResponse.class);
        if (response == null) {
            throw new RuntimeException("query follower openid failed, invalid response body");
        }
        if (response.getErrcode() != null && response.getErrcode() != 0) {
            throw new RuntimeException("query follower openid failed, errcode: " + response.getErrcode() + ", errmsg: " + response.getErrmsg());
        }
        if (response.getData() == null || response.getData().getOpenid() == null) {
            return Collections.emptyList();
        }
        return response.getData().getOpenid();
    }

    private String queryNickname(String accessToken, String openId) {
        try {
            URL infoUrl = new URL(String.format("https://api.weixin.qq.com/cgi-bin/user/info?access_token=%s&openid=%s&lang=zh_CN", accessToken, openId));
            HttpURLConnection conn = (HttpURLConnection) infoUrl.openConnection();
            conn.setRequestMethod("GET");
            String body = readResponseBody(conn);
            if (conn.getResponseCode() != 200) {
                return "unknown";
            }

            UserInfoResponse userInfoResponse = JSON.parseObject(body, UserInfoResponse.class);
            if (userInfoResponse == null) {
                return "unknown";
            }
            if (userInfoResponse.getErrcode() != null && userInfoResponse.getErrcode() != 0) {
                return "unknown";
            }
            return userInfoResponse.getNickname();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String readResponseBody(HttpURLConnection conn) throws Exception {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            if (conn.getErrorStream() == null) {
                throw e;
            }
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
        }

        try (BufferedReader closeableReader = reader) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = closeableReader.readLine()) != null) {
                content.append(line);
            }
            return content.toString();
        }
    }

    public static class WechatSendResponse {
        private Integer errcode;
        private String errmsg;

        public Integer getErrcode() {
            return errcode;
        }

        public void setErrcode(Integer errcode) {
            this.errcode = errcode;
        }

        public String getErrmsg() {
            return errmsg;
        }

        public void setErrmsg(String errmsg) {
            this.errmsg = errmsg;
        }
    }

    public static class FollowerListResponse {
        private Integer total;
        private Integer count;
        private OpenIdData data;
        private Integer errcode;
        private String errmsg;

        public Integer getTotal() {
            return total;
        }

        public void setTotal(Integer total) {
            this.total = total;
        }

        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        public OpenIdData getData() {
            return data;
        }

        public void setData(OpenIdData data) {
            this.data = data;
        }

        public Integer getErrcode() {
            return errcode;
        }

        public void setErrcode(Integer errcode) {
            this.errcode = errcode;
        }

        public String getErrmsg() {
            return errmsg;
        }

        public void setErrmsg(String errmsg) {
            this.errmsg = errmsg;
        }
    }

    public static class OpenIdData {
        private List<String> openid;

        public List<String> getOpenid() {
            return openid;
        }

        public void setOpenid(List<String> openid) {
            this.openid = openid;
        }
    }

    public static class UserInfoResponse {
        private String openid;
        private String nickname;
        private Integer errcode;
        private String errmsg;

        public String getOpenid() {
            return openid;
        }

        public void setOpenid(String openid) {
            this.openid = openid;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public Integer getErrcode() {
            return errcode;
        }

        public void setErrcode(Integer errcode) {
            this.errcode = errcode;
        }

        public String getErrmsg() {
            return errmsg;
        }

        public void setErrmsg(String errmsg) {
            this.errmsg = errmsg;
        }
    }

}
