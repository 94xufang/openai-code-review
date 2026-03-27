package com.xufun.sdk.utils;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class WXAccessTokenUtils {

    private static final Logger logger = LoggerFactory.getLogger(WXAccessTokenUtils.class);

    private static final String GRANT_TYPE = "client_credential";
    private static final String STABLE_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/stable_token";

    public static String getAccessToken(String appId, String secret) {
        try {
            logger.info("getting stable access token for appid: {}", appId);
            URL url = new URL(STABLE_TOKEN_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);

            StableTokenRequest request = new StableTokenRequest();
            request.setGrant_type(GRANT_TYPE);
            request.setAppid(appId == null ? null : appId.trim());
            request.setSecret(secret == null ? null : secret.trim());
            request.setForce_refresh(false);
            byte[] body = JSON.toJSONString(request).getBytes(StandardCharsets.UTF_8);

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body);
                outputStream.flush();
            }

            int responseCode = connection.getResponseCode();
            logger.info("stable access token response code: {}", responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                String responseBody = response.toString();
                logger.info("stable access token response: {}", responseBody);

                Token token = JSON.parseObject(responseBody, Token.class);
                if (token == null) {
                    logger.error("failed to parse stable access token response");
                    return null;
                }

                if (token.getErrcode() != null && token.getErrcode() != 0) {
                    logger.error("failed to get stable access token, errcode: {}, errmsg: {}", token.getErrcode(), token.getErrmsg());
                    return null;
                }

                if (token.getAccess_token() == null || token.getAccess_token().trim().isEmpty()) {
                    logger.error("stable access token is empty, response: {}", responseBody);
                    return null;
                }

                logger.info("stable access token obtained successfully, expires in: {} seconds", token.getExpires_in());
                return token.getAccess_token();
            } else {
                logger.error("stable token request failed with response code: {}", responseCode);
                return null;
            }
        } catch (Exception e) {
            logger.error("exception while getting stable access token", e);
            return null;
        }
    }

    public static class Token {
        private String access_token;
        private Integer expires_in;
        private Integer errcode;
        private String errmsg;

        public String getAccess_token() {
            return access_token;
        }

        public void setAccess_token(String access_token) {
            this.access_token = access_token;
        }

        public Integer getExpires_in() {
            return expires_in;
        }

        public void setExpires_in(Integer expires_in) {
            this.expires_in = expires_in;
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

    public static class StableTokenRequest {
        private String grant_type;
        private String appid;
        private String secret;
        private Boolean force_refresh;

        public String getGrant_type() {
            return grant_type;
        }

        public void setGrant_type(String grant_type) {
            this.grant_type = grant_type;
        }

        public String getAppid() {
            return appid;
        }

        public void setAppid(String appid) {
            this.appid = appid;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Boolean getForce_refresh() {
            return force_refresh;
        }

        public void setForce_refresh(Boolean force_refresh) {
            this.force_refresh = force_refresh;
        }
    }

}
