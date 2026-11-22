# 微信聊天归档器

一个只在 Android 本机保存数据并生成 Markdown 的微信聊天归档工具。应用可以继续监听授权后的
微信通知，也可以导入 Windows 版开源工具 Moect 导出的聊天 ZIP，把个人聊天、群聊、照片、
视频、语音、文档和 GIF/WebP 动画表情关联保存到同一时间线。

## 功能

- 捕获 `com.tencent.mm` 的新微信通知文字。
- 支持个人聊天、群聊、Android `MessagingStyle`、采集开关和会话白名单。
- 支持导入 Moect 导出的未加密 ZIP，可包含多个个人或群聊。
- 保存图片、视频、语音、文档、GIF/WebP 动画表情和其他文件。
- 使用本机 SQLite v2 保存消息及附件元数据，使用 SHA-256 生成附件文件名并去重。
- 支持会话、发送者、正文和附件名搜索，支持单条删除和全部清空。
- 消息详情显示附件类型、名称、大小和可用状态。
- 图片和动画表情可在应用内预览；视频、音频和文档可通过只读内容 URI 交给其他应用打开。
- 保存或分享 Markdown 时，会列出每个消息的附件名称、类型及是否已取得。
- 不需要电脑服务、账号或网络权限；应用没有 `INTERNET` 权限。

## 历史聊天从哪里来

Android 和微信均未提供读取历史聊天及原始媒体附件的公开接口。普通 Android 应用无法读取
微信私有数据库，本项目也不破解微信登录、数据库或加密格式。

完整历史需要通过 Windows 微信和开源桌面工具
[Moect](https://github.com/mofan6/Moect) 导出。Moect 与本项目没有官方隶属关系；本项目只读取其
导出的 ZIP/JSON 数据格式，没有复制 Moect 的 GPL-3.0 源代码。若以后将双方代码合并发布，需要
重新进行许可证审查。

### 准备 Moect ZIP

1. 在 Windows 10/11 登录自己的微信。
2. 启动 Moect，连接微信，并打开目标个人聊天或群聊。
3. 选择 JSON 导出，保留图片、动画表情、视频、语音和文件资源。不要使用“快速导出”或跳过
   附件，否则只能导入消息占位文本。
4. 将 JSON 和对应的 `*_assets` 资源目录打进同一个未加密 ZIP。Moect 自带的未加密 ZIP 也可
   直接使用。数字信封或密码保护的 AES ZIP 目前不支持。
5. 把 ZIP 复制到手机，在应用内点击“导入 Moect ZIP”。

如果 ZIP 保留批量子目录，导入器会以每个 JSON 所在目录为基准解析其资源路径，因此以下两种
结构都可识别：

```text
单会话：chat.json + chat_assets/...
批量子目录：会话A/chat.json + 会话A/chat_assets/...
```

## 安装

1. 将 `微信聊天归档器-v1.1.0.apk` 复制到 Android 手机。
2. 点击 APK，并允许当前文件管理器“安装未知应用”。
3. 安装并打开应用。
4. 若要实时采集，点击“打开通知访问设置”，找到“微信聊天归档器”并开启通知访问。
5. 若要导入历史，点击“导入 Moect ZIP”并选择准备好的 ZIP。

最低支持 Android 8.0（API 26）。

## 使用

- 微信收到新通知后，应用会把通知中可见的标题、摘要、会话和发送者写入本地数据库。
- 导入 Moect ZIP 时，会解析聊天文本、时间、发送者和附件引用。
- 点击列表中的消息查看全文和附件。点击已存储的图片或 GIF/WebP 表情可预览；其他附件会
  调用系统应用打开。
- “保存 Markdown”通过系统文件选择器写入完整归档。
- “分享 Markdown”通过系统分享面板发送真实的 `.md` 文件。
- 搜索只影响当前显示列表，Markdown 导出始终包含全部记录。
- 删除消息会同步删除其应用私有附件文件；“清空”会删除全部消息、附件记录和附件文件。

超过 500 条时列表只显示最近的 500 条，搜索也会在数据库结果中限制显示数量；Markdown 导出
始终包含全部记录。

## 数据边界

本应用不读取微信数据库，不修改微信客户端，不自动回复，也不绕过微信登录或加密。

因此以下内容可能无法归档：

- 安装、授权和导入前的通知；
- 被设置为免打扰或关闭通知的会话；
- 只显示“N 条新消息”的合并通知中的具体内容；
- 微信没有提供给 Moect、或 Moect 导出时未取得的原始附件；
- 没有有效读取凭据、仍处于加密状态或已经过期的媒体；
- 微信专有且无法解析的动画表情格式。无法解码时只能保留原始文件或标记为不支持。

Moect 能否取得媒体取决于微信本机缓存、可用凭据和原始文件状态。其 JSON 中标记为
`missing`、`encrypted`、`unsupported` 或 `corrupt` 的资源会在应用内显示相应状态，不会被
伪装成已存储文件。

历史记录仍应使用微信自带的“聊天记录迁移与备份”保存。通知和聊天中含有其他人的私人信息，
请仅在获得合理授权的前提下使用，并妥善保护导出的 Markdown、ZIP 和附件。

## 导入限制

- 只支持未加密 ZIP；单个 ZIP 最多 100,000 个条目。
- 单个 JSON 最多 64 MB，单个 ZIP 内 JSON 总量最多 256 MB。
- 单个附件最多 2 GB，单个 ZIP 附件总量最多 20 GB。
- ZIP 条目和 JSON 资源路径不得是绝对路径或包含 `..`。
- 同一 ZIP 可重复导入。消息通过稳定指纹去重，附件通过消息、源路径和显示名去重。
- 若首次导入时附件缺失，之后重新导入包含该文件的完整 ZIP，会把原记录从“未取得”升级为
  “已存储”，不会产生重复附件。

## 本地存储

- 消息与附件索引：应用私有 SQLite `wechat_archive.db`。
- 附件文件：应用私有 `files/attachments/<消息ID>/<SHA-256前缀>-<安全文件名>`。
- SQLite 从 v1 无损迁移到 v2；升级不会删除原通知记录。
- `allowBackup=false`，并显式排除云备份与设备迁移，避免聊天记录被系统自动复制。
- 分享附件时使用临时只读授权；附件 Provider 不对外公开写入能力。

## 技术结构

```text
微信通知
  -> WeChatNotificationListener
  -> 包名与会话白名单过滤
  -> 通知字段解析与去重指纹
  -> SQLite

Moect ZIP
  -> JSON 与资源路径解析
  -> 两遍 ZIP 读取（先解析，再复制附件）
  -> SHA-256 去重
  -> SQLite + 应用私有附件目录

SQLite
  -> Android 原生消息/附件界面
  -> Markdown 导出 / 系统文件保存 / 系统分享
```

关键代码：

- `domain/importer/MoectArchiveParser.java`：Moect JSON、消息和资源状态解析。
- `importing/MoectZipImporter.java`：ZIP 安全校验、两遍导入、附件复制和重复导入处理。
- `importing/AttachmentStore.java`：应用私有附件存储、哈希、路径边界校验和删除。
- `data/ArchiveDatabase.java`：SQLite v2、消息/附件关联、查询和级联删除。
- `export/AttachmentContentProvider.java`：只读附件 URI。
- `service/WeChatNotificationListener.java`：通知字段提取，兼容 API 26–29。
- `domain/MarkdownExporter.java`：Markdown 内容和附件清单生成。

## 构建与测试

```powershell
.\verify.ps1
.\verify.ps1 -Release
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

项目路径包含中文时，Windows 上 AGP 的 JVM 测试执行器可能出现类路径编码问题。
`verify.ps1` 会临时把项目映射到一个空闲 ASCII 盘符来运行单元测试和 Android Lint，结束后
自动解除映射。

Android 仪器测试需要已启动的模拟器或设备：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

测试覆盖通知解析、Moect JSON、附件分类、路径安全、ZIP 导入、重复导入、SQLite 迁移、
附件持久化、只读 Provider、Markdown 导出、采集策略和白名单。具体数量以每次测试报告为准。

交付的 release APK 使用本机 Android Debug 证书完成个人安装签名。该 APK 可正常安装，但
不可用于应用商店发布。若以后更换签名，请先导出已有记录，再卸载旧版本安装新版本。
