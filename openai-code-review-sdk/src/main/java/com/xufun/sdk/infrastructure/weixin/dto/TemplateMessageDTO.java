package com.xufun.sdk.infrastructure.weixin.dto;

import java.util.HashMap;
import java.util.Map;

public class TemplateMessageDTO {
    private String touser;//收信人id
    private String template_id;//模板消息ID
    private String url;//跳转地址
    private Map<String, Map<String, String>> data;

    //构造方法
    public TemplateMessageDTO(String touser, String template_id, String url, Map<String, Map<String, String>> data) {
        this.touser = touser;
        this.template_id = template_id;
        this.url = url;
        this.data = data;
    }

    public static void put(Map<String, Map<String, String>> data, TemplateKey key, String value) {
        Map<String, String> inner = new HashMap<>();
        inner.put("value", value);
        data.put(key.getCode(), inner);
    }

    public String getTouser() {
        return touser;
    }

    public String getTemplate_id() {
        return template_id;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, Map<String, String>> getData() {
        return data;
    }

    public enum TemplateKey {
        REPO_NAME("repo_name","项目名称"),
        BRANCH_NAME("branch_name","分支名称"),
        COMMIT_AUTHOR("commit_author","提交者"),
        COMMIT_MESSAGE("commit_message","提交信息"),
        ;

        private String code;
        private String desc;

        TemplateKey(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public String getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }
    }

}
