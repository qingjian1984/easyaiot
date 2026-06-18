package com.basiclab.iot.sink.service.impl;

import com.basiclab.iot.sink.service.PostProcessWorkerResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 从 node_workload_binding + compute_node 解析后处理 Worker HTTP 地址
 */
@Slf4j
@Service
public class PostProcessWorkerResolverImpl implements PostProcessWorkerResolver {

    private static final String WORKLOAD_PREFIX = "pp_";

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Value("${basiclab.post-process.worker-http-port:19680}")
    private int workerHttpPort;

    @Value("${basiclab.post-process.local-worker-url:}")
    private String localWorkerUrl;

    private final ConcurrentHashMap<Integer, AtomicInteger> roundRobin = new ConcurrentHashMap<>();

    @Override
    public String resolveWorkerBaseUrl(Integer taskId) {
        List<String> urls = listWorkerBaseUrls(taskId);
        if (urls.isEmpty()) {
            if (StringUtils.hasText(localWorkerUrl)) {
                return normalizeBaseUrl(localWorkerUrl);
            }
            return "http://127.0.0.1:" + workerHttpPort;
        }
        int index = roundRobin
                .computeIfAbsent(taskId, k -> new AtomicInteger(0))
                .getAndIncrement() % urls.size();
        return urls.get(Math.abs(index));
    }

    @Override
    public List<String> listWorkerBaseUrls(Integer taskId) {
        List<String> urls = new ArrayList<>();
        if (jdbcTemplate == null || taskId == null) {
            return urls;
        }
        try {
            String pattern = WORKLOAD_PREFIX + taskId + "_r%";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT cn.host AS host, nwb.workload_id AS workload_id "
                            + "FROM node_workload_binding nwb "
                            + "JOIN compute_node cn ON cn.id = nwb.node_id "
                            + "WHERE nwb.workload_type = 'post_process' "
                            + "AND nwb.workload_id LIKE ? "
                            + "AND (nwb.deleted = 0 OR nwb.deleted IS NULL) "
                            + "AND cn.deleted = 0 "
                            + "ORDER BY nwb.workload_id",
                    pattern);
            for (Map<String, Object> row : rows) {
                Object hostObj = row.get("host");
                if (hostObj == null) {
                    continue;
                }
                String host = hostObj.toString().trim();
                if (host.isEmpty()) {
                    continue;
                }
                int port = workerHttpPort;
                Object workloadIdObj = row.get("workload_id");
                if (workloadIdObj != null) {
                    String workloadId = workloadIdObj.toString();
                    int replicaIndex = parseReplicaIndex(workloadId);
                    if (replicaIndex >= 0) {
                        port = workerHttpPort + replicaIndex;
                    }
                }
                urls.add("http://" + host + ":" + port);
            }
        } catch (Exception e) {
            log.warn("解析后处理 Worker 地址失败 taskId={}: {}", taskId, e.getMessage());
        }
        return urls;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static int parseReplicaIndex(String workloadId) {
        if (!StringUtils.hasText(workloadId)) {
            return -1;
        }
        int idx = workloadId.lastIndexOf("_r");
        if (idx < 0 || idx + 2 >= workloadId.length()) {
            return -1;
        }
        try {
            return Integer.parseInt(workloadId.substring(idx + 2));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
