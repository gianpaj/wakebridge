# Agent Note: Single-purpose wake boundary

Status: implemented

## Problem

从不受信任的移动网络唤醒 gianRTX，需要穿过公网进入私有局域网。通用远程控制服务会增加本系统不需要的目标、命令、凭据和故障模式。Android 小组件还需要短时、可靠的异步执行，同时不能把应用变成永久后台服务。

## Decision

仓库交付两个独立组件，它们只通过包含两个操作的 HTTP 契约连接。Kotlin Multiplatform 客户端可以用 `GET /health` 测试连接，也可以用 `POST /wake` 请求唤醒。Go 服务端把固定的唤醒请求映射到启动时已验证的唯一 MAC 地址、IPv4 广播地址和 UDP 端口。调用方不能选择目标，也不能提供任何在服务端执行的数据。

Cloudflare Access 对公网 HTTPS 请求进行认证，Tunnel 将请求转发到 Go 服务的回环监听地址。Go 服务不了解 Cloudflare，并要求独立的 Bearer Token。客户端拒绝明文 HTTP 和重定向；Android 使用不可导出的 Android Keystore 密钥加密已测试的配置。

Glance 小组件不会把凭据放入小组件状态或 WorkManager 输入。操作回调会加入一个具有唯一名称和网络约束的任务；Worker 解密配置并发出一次请求。它只记录少量显示状态，并且不会重试结果不明确的唤醒响应。

## Runtime boundaries

`server-go` 是独立的 Go 模块，构建为单个 Linux ARM64 可执行文件。`mobile` 是独立的 Gradle 构建，包含共享 KMP 领域与网络模块、Android 应用和 iOS framework 目标。两个构建系统互不调用。

服务端负责目标身份和 Wake-on-LAN 发送。Cloudflare 负责公网 TLS、Access 策略和隧道传输。共享 Kotlin 负责 URL 验证和 HTTP 线协议。Android 负责 Keystore 持久化、Compose UI、Glance 和 WorkManager 执行。

## Alternatives considered

**由手机直接发送 Wake-on-LAN。** 互联网路由器通常不会转发局域网广播，暴露 UDP 广播发送也会削弱安全边界。因此，由持续在线的 Jetson 在局域网内发送。

**可复用命令或多机器 API。** 客户端提供命令、MAC 地址、主机名或目标地址，会把固定功能变成远程控制面。V1 明确放弃这种灵活性。

**仅通过 VPN 访问。** Tailscale 或其他 VPN 可以保护路由，但 V1 的成功条件要求手机不使用 VPN，通过普通蜂窝网络点击小组件。Cloudflare Access 和应用 Token 提供两层检查。

**永久 Android 服务或自动重试请求。** 单个短请求不需要永久服务。响应丢失后重试可能在首次请求已到达服务端时再次发送命令，因此 WorkManager 只尝试一次，并把失败留给用户手动处理。

## Verification

Go 测试覆盖启动配置验证、Magic Packet 字节、认证、路由、HTTP 方法和唤醒失败。服务端可构建为静态 Linux ARM64 二进制文件。共享 Kotlin 测试覆盖 HTTPS 验证和请求头；Android 调试 APK 与 iOS 模拟器 framework 目标均可编译。

实体蜂窝网络测试仍属于外部验证，因为它需要已配置的 Jetson、Cloudflare 账户、Android 手机、gianRTX 固件和已供电的网卡。

## Consequences

系统的心智模型和攻击面都很小：一个公网主机名、一个应用密钥、一个配置目标和一个操作。Go 服务不需要 root 权限或持久数据，Android 小组件也不需要常驻进程。

代价是有意的刚性。增加另一台机器、检查 gianRTX 是否在线、确认实际启动、重试发送，或支持 iOS 安全存储和 WidgetKit，都需要后续决策。系统还依赖 Jetson、Cloudflare Tunnel、Access 策略和 gianRTX 的 Wake-on-LAN 支持持续保持正确配置。
