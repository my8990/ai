package com.example.ai.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GeocodeUtil {

    private static final String AMAP_REVERSE_URL = "https://restapi.amap.com/v3/geocode/regeo";

    private static final int CACHE_MAX_SIZE = 2000;

    private static final long NEGATIVE_TTL_MILLIS = 30_000L;

    private static final Map<String, CacheEntry> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > CACHE_MAX_SIZE;
                }
            });

    private final RestTemplate restTemplate;

    public GeocodeUtil(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Value("${amap.key:}")
    private String amapKey;

    public String reverse(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) return null;
        if (amapKey == null || amapKey.isEmpty()) return null;

        String cacheKey = String.format("%.5f,%.5f", latitude, longitude);
        CacheEntry cached = CACHE.get(cacheKey);
        if (cached != null && cached.isValid()) {
            return cached.value();
        }

        try {
            String lngStr = longitude.toPlainString();
            String latStr = latitude.toPlainString();
            String url = AMAP_REVERSE_URL
                    + "?key=" + amapKey
                    + "&location=" + lngStr + "," + latStr
                    + "&extensions=all"
                    + "&radius=500";

            Map<?, ?> resp = restTemplate.getForObject(url, Map.class);
            if (resp == null || !"1".equals(String.valueOf(resp.get("status")))) {
                log.warn("高德逆地理返回异常: lat={}, lng={}, resp={}", latStr, lngStr, resp);
                putNegative(cacheKey);
                return null;
            }

            Map<?, ?> regeocode = (Map<?, ?>) resp.get("regeocode");
            String result = pickBestAddress(regeocode);

            log.info("高德逆地理: lat={}, lng={} => {}", latStr, lngStr, result);

            if (result != null && !result.isBlank()) {
                CACHE.put(cacheKey, new CacheEntry(result, Long.MAX_VALUE));
                return result;
            }
            putNegative(cacheKey);
            return null;

        } catch (Exception e) {
            log.warn("高德逆地理请求失败: lat={}, lng={}, err={}", latitude, longitude, e.getMessage());
            putNegative(cacheKey);
            return null;
        }
    }

    private void putNegative(String cacheKey) {
        CACHE.put(cacheKey, new CacheEntry(null, System.currentTimeMillis() + NEGATIVE_TTL_MILLIS));
    }

    private record CacheEntry(String value, long expiresAt) {

        boolean isValid() {
            return expiresAt == Long.MAX_VALUE || System.currentTimeMillis() < expiresAt;
        }
    }

    /**
     * 和前端 request.js 的 reverseGeocode 选地址策略完全一致：
     *   poiName || formattedAddress
     *   优先 POI 名（最近、最相关），没有 POI 再用 formatted_address
     */
    @SuppressWarnings("unchecked")
    private String pickBestAddress(Map<?, ?> regeocode) {
        if (regeocode == null) return null;

        // 1. 选最好的 POI 名（高德已按距离排序，取最近的有效 POI）
        String poiName = null;
        Object poisObj = regeocode.get("pois");
        if (poisObj instanceof List) {
            List<Map<String, Object>> pois = (List<Map<String, Object>>) poisObj;
            for (Map<String, Object> poi : pois) {
                if (poi == null) continue;
                Object name = poi.get("name");
                if (name != null && !name.toString().isBlank()) {
                    poiName = name.toString();
                    break;
                }
            }
        }

        // 2. formatted_address 兜底
        String formatted = null;
        Object fa = regeocode.get("formatted_address");
        if (fa != null && !fa.toString().isBlank()) {
            formatted = fa.toString();
        }

        // 3. POI 名优先
        if (poiName != null) return poiName;
        return formatted;
    }

    public String ensureAddress(String existing, BigDecimal lat, BigDecimal lng) {
        String backendResult = reverse(lat, lng);
        if (backendResult != null && !backendResult.isBlank()) {
            return backendResult;
        }
        if (existing != null && !existing.isBlank()
                && !existing.equals("课堂签到")
                && !existing.equals("null")) {
            return existing.trim();
        }
        return null;
    }
}