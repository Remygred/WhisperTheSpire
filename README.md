# WhispersTheSpire 使用说明

这是一个《Slay the Spire》的辅助决策 Mod。它会在游戏中显示一个小窗，基于你当前的局势给出路线、选牌、商店等建议。
如果你只是想玩，照着下面做就可以。

## 你需要准备
1. 已安装《Slay the Spire》（Steam 版即可）
2. 已安装 ModTheSpire（MTS）与 BaseMod
   如果你平时就用其他 Mod，这两项通常已经有了

## 安装步骤（普通玩家）
1. 把 `WhispersTheSpire-*.jar` 放到游戏的 `mods` 文件夹
   路径通常是：
   `E:\SteamLibrary\steamapps\common\SlayTheSpire\mods`
2. 运行游戏目录里的 **ModTheSpire 启动器**
3. 在 Mod 列表中勾选 `BaseMod` 与 `WhispersTheSpire`
4. 启动游戏

## Release 下载说明
如果你不想自己编译，可以直接下载 Release 里的 jar：
1. 打开项目的 GitHub Release 页面
2. 下载最新版本的 `WhispersTheSpire-*.jar`
3. 放进游戏 `mods` 文件夹（见上面的安装步骤）

Release 页面地址：
```
https://github.com/Remygred/WhisperTheSpire/releases
```

## 第一次配置（重要）
1. 主菜单点 `Mods`
2. 选中 `WhispersTheSpire`，点击 `Config`
3. 填写或确认：
   - `baseUrl`：你的接口地址（例如 `https://api.lonlie.cn/v1`）
   - `model`：模型名称（例如 `gpt-4o-mini`）
   - `apiKey`：你的密钥（不会写入日志）
4. 语言可在设置里一键切换（中文/英文）

## 游戏内怎么用
1. 小窗默认在右侧，可拖动、可缩放
2. 按 `F8` 手动刷新（请求一次模型）
3. 按 `F9` 显示/隐藏小窗
4. 标题栏按钮：
   - `刷新`：等同于 F8
   - `隐藏`：等同于 F9

## 自动触发（可选）
在设置里打开 `自动触发` 后，进入地图/选牌/商店/休息等场景会自动给建议。
如果不想频繁请求，可以关掉，随时用 F8 手动刷新。

## 常见问题
1. 看不到 Mod？
   - 确认 jar 在 `mods` 目录
   - 确认 `BaseMod` 与 `WhispersTheSpire` 已勾选
2. 接口报错？
   - 检查 `baseUrl` 是否正确（通常以 `/v1` 结尾）
   - 检查 `apiKey` 是否有效
3. 没有建议返回？
   - 先按 F8 试一次
   - 查看小窗状态行是否有错误提示

---

## 一点小话
希望你玩得开心，欢迎多多游玩。
如果遇到 bug 或问题，欢迎反馈。
也欢迎其他开发者一起参与改进。

---

## 开发者说明（给想参与改进的人）
如果你想自己修改或二次开发，这里是快速上手指南。

### 项目结构速览
- `src/main/java/whispers/thespire/`
  - `WhispersTheSpireMod.java`：Mod 入口，注册设置面板与渲染
  - `ui/OverlayPanel.java`：小窗 UI、滚动条、按钮、文本渲染
  - `state/StateExtractor.java`：从游戏对象提取状态
  - `state/SnapshotManager.java`：快照生成与裁剪（控制 JSON 大小）
  - `llm/`：调用模型、构造 prompt、解析返回
  - `logic/TriggerManager.java`：自动触发逻辑（地图/选牌/商店等）
  - `patches/`：必要的游戏 Hook（如药水溢出检测）
  - `i18n/`：语言切换文本
- `src/main/resources/`
  - `ModTheSpire.json`：Mod 元信息
  - `images/`：徽章图
  - `img/`：UI 资源（例如背景）

### 开发环境准备
1. 安装 JDK 8（游戏本体是 Java 8）
2. 安装 ModTheSpire + BaseMod
3. 复制依赖 jar：
   `powershell -ExecutionPolicy Bypass -File .\scripts\copy_deps.ps1`

### 常用命令
1. 校验依赖 + 构建：
   `.\gradlew verifyDeps build`
2. 部署到游戏：
   `.\gradlew deployToGameMods`

### 改代码后的测试流程
1. 运行构建 + 部署
2. 打开 ModTheSpire 启动器
3. 勾选 BaseMod + WhispersTheSpire
4. 进入游戏验证：
   - 地图 / 选牌 / 商店 / 事件 / 休息处
   - 小窗是否正常显示与更新

### 上手建议（从哪里改起）
1. 想改输出内容 → `llm/PromptBuilder.java`
2. 想改游戏信息 → `state/StateExtractor.java`
3. 想改界面 → `ui/OverlayPanel.java`
4. 想改自动触发 → `logic/TriggerManager.java`

### 常见报错排查
1. 启动时报 `NoClassDefFoundError`
   - 说明加载的 jar 版本不对或不完整
   - 先删掉旧的 `mods/WhispersTheSpire-*.jar`
   - 再执行 `.\gradlew deployToGameMods` 并重启 MTS
2. `verifyDeps` 失败
   - 通常是缺少 `desktop-1.0.jar / BaseMod.jar / ModTheSpire.jar`
   - 运行 `.\scripts\copy_deps.ps1` 重新复制依赖
3. 模型请求失败（SSL/超时）
   - 检查 `baseUrl` 是否正确，是否能在浏览器访问
   - 检查 `apiKey` 是否有效
   - 若是自建中转服务，确认 TLS/证书配置正常
4. 小窗不显示
   - 确认设置里 `显示小窗` 已开启
   - 按 `F9` 切换显示/隐藏

### 日志位置（排查用）
- 通常在游戏根目录：
  `yourpath\SteamLibrary\steamapps\common\SlayTheSpire\ModTheSpire.log`
- 有些情况下也会生成 `log.txt`（同目录）

### 小窗字段说明
- `screen`：当前界面（MAP/选牌/商店/事件/休息处等）
- `floor`：当前层数
- `asc`：升阶（A0、A1…）
- `hp`：当前生命 / 最大生命
- `gold`：金币
- `snapshot ok size=xxxx hash=xxxx`：
  - `size`：本次快照 JSON 的长度
  - `hash`：快照指纹（用于去重/缓存）
- `last=xxs, context=...`：
  - `last`：上一次成功返回距离现在的秒数
  - `context`：触发来源（手动/地图/选牌/商店/战斗等）
- `Summary`：模型给出的简要结论
- `1) / 2) / 3)`：模型建议列表
- `reason`：建议理由（可在设置里开关）
- `Next pick` / `路线规划`：
  - 只在地图规划时出现
  - `Next pick` 表示“从左往右第几个节点”
