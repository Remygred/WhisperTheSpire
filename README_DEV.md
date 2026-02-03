# WhispersTheSpire 开发指引（Phase 0）

## 依赖准备
在项目根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\copy_deps.ps1
```

该脚本会把 `desktop-1.0.jar`、`BaseMod.jar`、`ModTheSpire.jar` 复制到 `lib\`，并且重命名为固定文件名。
脚本会检测并清理项目根目录下同名 jar（避免依赖歧义）。
提示：如果这些 jar 之前被 git 追踪过，请执行 `git rm --cached lib/*.jar`（只写提示，不要在脚本中执行）。

## 构建与校验

```powershell
.\gradlew verifyDeps build
```

`verifyDeps` 会检查 `lib\` 下是否缺少依赖，如果缺失会直接失败并提示运行 `scripts\copy_deps.ps1`。

## 部署到游戏 Mods

```powershell
.\gradlew deployToGameMods
```

该任务会把 `build\libs\` 下的主产物 jar 复制到：

```
E:\SteamLibrary\steamapps\common\SlayTheSpire\mods
```

## 启动 ModTheSpire
在 Windows 下运行游戏目录下 MTS 启动器。

## 确认 Mods 列表
在 MTS 启动器中勾选 BaseMod 与 WhispersTheSpire，进入主菜单后打开 Mods 列表，确认能看到 WhispersTheSpire。
