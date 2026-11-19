package com.example.ai.util;

import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WxApiUtil {

    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String WXACODE_URL = "https://api.weixin.qq.com/wxa/getwxacodeunlimit";

    private static final int ERR_PAGE_NOT_EXIST = 41030;
    private static final int ERR_TOKEN_INVALID = 40001;
    private static final int ERR_TOKEN_EXPIRED = 42001;
    private static final int ERR_TOKEN_ILLEGAL = 40014;

    @Value("${wechat.appid}")
    private String appid;

    @Value("${wechat.secret}")
    private String secret;

    @Value("${wechat.wxacode-env:release}")
    private String envVersion;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String accessToken;
    private volatile long tokenExpireAt = 0L;

    public synchronized String getAccessToken(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && accessToken != null && now < tokenExpireAt) {
            return accessToken;
        }
        String url = TOKEN_URL + "?grant_type=client_credential&appid=" + appid + "&secret=" + secret;
        String body;
        try {
            body = restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.error("获取微信 access_token 失败", e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "微信服务异常");
        }
        JsonNode json = readJson(body);
        if (json.has("errcode")) {
            log.error("获取微信 access_token 被拒绝: {}", body);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "微信配置错误: " + json.path("errmsg").asText());
        }
        String token = json.path("access_token").asText();
        if (token.isEmpty()) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "微信未返回 access_token");
        }
        long expiresIn = json.path("expires_in").asLong(7200L);
        accessToken = token;
        tokenExpireAt = System.currentTimeMillis() + Math.max(expiresIn - 200L, 60L) * 1000L;
        return accessToken;
    }

    public String getUnlimitedCode(String scene, String page, int width) {
        if (scene == null || scene.isEmpty() || scene.length() > 32) {
            throw new BizException(ErrorCode.BAD_REQUEST, "小程序码 scene 长度需为 1-32 个字符");
        }
        CodeResponse resp = requestCode(getAccessToken(false), scene, page, width, envVersion);
        if (resp.dataUrl != null) {
            return resp.dataUrl;
        }
        if (isTokenInvalid(resp.errcode)) {
            resp = requestCode(getAccessToken(true), scene, page, width, envVersion);
            if (resp.dataUrl != null) {
                return resp.dataUrl;
            }
        }
        if (resp.errcode == ERR_PAGE_NOT_EXIST) {
            for (String env : fallbackEnvs()) {
                CodeResponse alt = requestCode(getAccessToken(false), scene, page, width, env);
                if (alt.dataUrl != null) {
                    log.info("小程序码在 env_version={} 下生成成功（配置值为 {}）", env, envVersion);
                    return alt.dataUrl;
                }
                resp = alt;
            }
        }
        throw new BizException(ErrorCode.INTERNAL_ERROR, describe(resp));
    }

    private List<String> fallbackEnvs() {
        List<String> list = new ArrayList<>();
        for (String env : new String[]{"develop", "trial", "release"}) {
            if (!env.equals(envVersion)) {
                list.add(env);
            }
        }
        return list;
    }

    private boolean isTokenInvalid(int errcode) {
        return errcode == ERR_TOKEN_INVALID || errcode == ERR_TOKEN_EXPIRED || errcode == ERR_TOKEN_ILLEGAL;
    }

    private String describe(CodeResponse resp) {
        String reason = resp.errmsg == null || resp.errmsg.isEmpty() ? "" : " " + resp.errmsg;
        if (resp.errcode == 0) {
            return "小程序码生成失败" + reason;
        }
        if (resp.errcode == ERR_PAGE_NOT_EXIST) {
            return "小程序码生成失败: 页面不存在于任何版本(41030)，请确认已上传含该页面的版本并设置 wechat.wxacode-env";
        }
        return "小程序码生成失败: " + resp.errcode + reason;
    }

    private CodeResponse requestCode(String token, String scene, String page, int width, String env) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("scene", scene);
        payload.put("page", page);
        payload.put("check_path", false);
        payload.put("env_version", env);
        payload.put("width", width);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<byte[]> resp = restTemplate.postForEntity(
                    WXACODE_URL + "?access_token=" + token,
                    new HttpEntity<>(payload, headers),
                    byte[].class
            );
            byte[] body = resp.getBody();
            if (body == null || body.length == 0) {
                return CodeResponse.error(0, "微信返回内容为空");
            }
            if (looksLikeJson(body)) {
                return parseError(body);
            }
            String subtype = imageSubtype(body);
            return CodeResponse.ok("data:image/" + subtype + ";base64," + Base64.getEncoder().encodeToString(body));
        } catch (Exception e) {
            log.error("调用小程序码接口异常", e);
            return CodeResponse.error(0, e.getMessage());
        }
    }

    private boolean looksLikeJson(byte[] body) {
        for (byte b : body) {
            if (b == ' ' || b == '\n' || b == '\r' || b == '\t') {
                continue;
            }
            return b == '{';
        }
        return false;
    }

    private String imageSubtype(byte[] body) {
        if (body.length >= 3 && body[0] == (byte) 0xFF && body[1] == (byte) 0xD8) {
            return "jpeg";
        }
        if (body.length >= 4 && body[0] == (byte) 0x89 && body[1] == 0x50
                && body[2] == 0x4E && body[3] == 0x47) {
            return "png";
        }
        return "png";
    }

    private CodeResponse parseError(byte[] body) {
        String text = new String(body);
        log.warn("生成小程序码失败: {}", text);
        try {
            JsonNode json = objectMapper.readTree(text);
            return CodeResponse.error(
                    json.path("errcode").asInt(0),
                    json.path("errmsg").asText("")
            );
        } catch (Exception e) {
            return CodeResponse.error(0, text);
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "微信接口返回内容无法解析");
        }
    }

    private static class CodeResponse {

        private final String dataUrl;
        private final int errcode;
        private final String errmsg;

        private CodeResponse(String dataUrl, int errcode, String errmsg) {
            this.dataUrl = dataUrl;
            this.errcode = errcode;
            this.errmsg = errmsg;
        }

        static CodeResponse ok(String dataUrl) {
            return new CodeResponse(dataUrl, 0, "");
        }

        static CodeResponse error(int errcode, String errmsg) {
            return new CodeResponse(null, errcode, errmsg);
        }
    }
}