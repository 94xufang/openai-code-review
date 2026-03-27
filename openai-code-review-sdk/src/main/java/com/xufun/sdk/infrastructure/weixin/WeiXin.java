package com.xufun.sdk.infrastructure.weixin;

import com.alibaba.fastjson2.JSON;
import com.xufun.sdk.infrastructure.weixin.dto.TemplateMessageDTO;
import com.xufun.sdk.utils.WXAccessTokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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
        TemplateMessageDTO templateMessageDTO = new TemplateMessageDTO(
                touser == null ? null : touser.trim(),
                template_id == null ? null : template_id.trim(),
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
            throw new RuntimeException("wechat template message send failed, errcode: "
                    + sendResponse.getErrcode() + ", errmsg: " + sendResponse.getErrmsg());
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

}
