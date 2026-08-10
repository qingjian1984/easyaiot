# 电力物模型写链启动门禁证据（2026-08-10）

- 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0。
- 配置组合测试：`PowerModelActivationGuardTest` 7/7 PASS。
- Controller 默认关闭门禁：`PowerModelBindingControllerGateTest` 1/1 PASS。
- 启动门禁、Controller、真实 PG 首发、第四端口与事件处理器完整组合回归：26/26 PASS。
- Java 17 reactor compile：PASS。
- Docker Compose 配置解析：PASS。
- 安全反例：API 缺第四端口、API 缺事件链、事件链缺第四端口、mini 激活、capability 关闭、secret 空或不足 32 UTF-8 字节均启动失败。
- 正例：standard/full 完整四事实通过；standard/full 可先单独装配第四端口，再开启事件链。
- 运行状态：三个开关均未启用；未注入真实 secret；未执行 DDL；未调用 NODE。
- 评审结论：`CONDITIONALLY_APPROVED / NOT_ACTIVATED`。
- 最终残留：测试租户八类事实 `0/0/0/0/0/0/0/0`；业务计数 `4/4/17`。
