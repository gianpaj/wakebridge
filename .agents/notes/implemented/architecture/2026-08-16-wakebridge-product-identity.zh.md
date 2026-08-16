# Agent Note: WakeBridge product identity

Status: implemented

## Problem

原项目名称同时出现在包名、可执行文件路径、systemd 资源、安全存储标识、小组件类和 KMP framework 名称中。只修改可见标签会导致部署和源码标识不一致。同时，`gianRTX` 是已配置的机器，而不是产品名称，因此全局替换会改变固定的 API 目标。

## Decision

产品命名为 **WakeBridge**，其描述是一个自托管的 Android 按钮和小组件，用于从任何地方安全地唤醒 PC。面向产品的源码和运行时标识统一使用 `WakeBridge` 或 `wakebridge`。

Go 命令是 `cmd/wakebridge-server`。部署使用 `/usr/local/bin/wakebridge-server`、`/etc/wakebridge.env`、`wakebridge.service` 和无特权的 `wakebridge` 系统用户。

Android 使用 `dev.gianpaj.wakebridge` 应用与包命名空间、`WakeBridgeApplication` 和 `WakeBridgeWidget`。共享 Kotlin framework 名称是 `WakeBridgeShared`，iOS 占位应用命名为 `WakeBridge`。

唯一配置的电脑仍然是 `gianRTX`。环境变量 `GIANRTX_MAC`、**Wake gianRTX** 操作和 API 响应中的目标保持不变。本次重命名不会扩大或以其他方式更改 HTTP 契约。

由于该仓库尚未发布应用或部署，本次不会为旧标识保留兼容别名或存储迁移。

## Alternatives considered

**只重命名可见标签。** 这会使安装命令、服务名称、包命名空间和生成产物继续使用旧标识。

**同时重命名已配置目标。** 产品重命名不会改变服务端唤醒的机器。泛化或重命名目标属于独立的行为与 API 决策。

**保留兼容别名。** 为尚未发布的标识增加别名和迁移会产生额外运维路径。如果以后出现真实兼容需求，再引入这些机制。

## Consequences

源码、构建产物、Android 身份和 Jetson 部署现在使用同一个易识别的产品名称。旧名称下的本地开发安装或手动测试部署必须被替换，不能原地升级。系统狭窄的单目标行为保持不变。
