# AGENTS.md

## 规则

1. 使用中文沟通。
2. 每一个类、文件或模块应当仅负责一项明确的职责。
3. 当检测到某个文件过于臃肿时，必须主动提出重构和拆分方案，严禁继续往该文件中追加新逻辑。

## 结构说明

- `app/src/main/java/com/moonlib/cosmos/MainActivity.kt`：应用入口 Activity，全局主题注入处。
- `app/src/main/java/com/moonlib/cosmos/data/ai`：统一 AI 通讯基础层，负责请求发送、固定顺序 Prompt 组装、JSON Schema、响应清洗、通讯日志与状态卡更新。
- `app/src/main/java/com/moonlib/cosmos/data/chat`：聊天联系人、消息模型、AI 回复解析、聊天引擎 / 仓库，以及朋友圈动态模型、生成引擎与持久化仓库。
- `app/src/main/java/com/moonlib/cosmos/data/context`：跨聊天、互动、日记、推特与朋友圈等场景的统一宽历史构建与裁剪逻辑；业务 Engine 不应私自拼接角色历史。
- `app/src/main/java/com/moonlib/cosmos/data/interaction`：互动消息、消息合并、互动配置、互动仓库、单人与多人互动生成引擎。
- `app/src/main/java/com/moonlib/cosmos/data/profile`：角色档案模型、AI 档案生成与档案持久化仓库。
- `app/src/main/java/com/moonlib/cosmos/data/settings`：AI 服务配置、模型参数、响应解析、授权头、Vertex 认证与端点解析、思考等级请求参数转换、模型列表缓存、日志、存档、系统提示词与主题配置持久化仓库。
- `app/src/main/java/com/moonlib/cosmos/data/time`：系统时间状态、推进逻辑与基于时间跳过的内容生成编排。
- `app/src/main/java/com/moonlib/cosmos/data/diary`：日记数据模型、多槽存档隔离日记仓库与日记 AI 生成引擎。
- `app/src/main/java/com/moonlib/cosmos/data/memory`：长期记忆数据模型、记忆持久化仓库、AI 通讯返回记忆字段解析与上下文格式化逻辑。
- `app/src/main/java/com/moonlib/cosmos/data/twitter`：推特式动态的数据模型、用户资料、持久化仓库与 AI 生成引擎。
- `app/src/main/java/com/moonlib/cosmos/ui/chat`：聊天应用入口、主列表、会话页、附件面板、朋友圈列表 / 详情、特殊消息气泡、联系人编辑、联系人信息卡、头像组件与回复揭示动效。
- `app/src/main/java/com/moonlib/cosmos/ui/common`：跨界面复用的通用 UI / Insets 工具。
- `app/src/main/java/com/moonlib/cosmos/ui/desktop`：桌面主界面、应用网格、应用图标、桌面时钟、状态栏与自适应壁纸。
- `app/src/main/java/com/moonlib/cosmos/ui/interaction`：互动应用入口、互动列表、单人会话、多人共享互动、互动输入 / 消息组件与互动设置界面。
- `app/src/main/java/com/moonlib/cosmos/ui/profile`：角色档案应用入口、档案列表、档案编辑、AI 灵感输入与档案界面状态。
- `app/src/main/java/com/moonlib/cosmos/ui/settings`：设置入口、AI 聊天设置、模型服务配置、服务商选择、模型选择、思考等级选择、日志列表 / 日志详情、存档、系统提示词与主题配置界面。
- `app/src/main/java/com/moonlib/cosmos/ui/theme`：Compose 主题、配色集（深浅双色板）与 CompositionLocal 定义。
- `app/src/main/java/com/moonlib/cosmos/ui/time`：时间应用界面。
- `app/src/main/java/com/moonlib/cosmos/ui/diary`：日记应用相关界面。包含入口控制页 (`DiaryAppScreen.kt`)、日记内容卡片与快照组件 (`DiaryCard.kt`)、底部已选标签与输入条 (`DiaryInputBar.kt`)、配置弹窗 (`DiarySettingsDialog.kt`) 及参与者多选弹窗 (`DiaryAtCharacterDialog.kt`) 等。
- `app/src/main/java/com/moonlib/cosmos/ui/memory`：记忆应用相关界面。包含入口控制页、记忆列表、详情弹窗、编辑弹窗与角色筛选。
- `app/src/main/java/com/moonlib/cosmos/ui/twitter`：推特式动态应用入口、时间线、发现页、发帖弹窗、资料编辑与帖子详情线程界面。
- `app/src/main/java/com/moonlib/cosmos/utils`：跨层级复用的工具函数。
- `app/src/main/res`：Android 资源文件。
- `app/src/test`：本地单元测试。
- `app/src/androidTest`：Android 仪器测试。
- `gradle`：Gradle Wrapper 与版本目录配置。

## 维护约定

- 新增功能时优先放入与职责匹配的包中；如果没有合适位置，应先创建清晰的新包或模块边界。
- UI 组件、数据模型、数据访问、业务逻辑应保持分离，避免在单个 Composable 或 Activity 中堆叠过多职责。
- 修改现有文件前，如果发现文件已经承担多个职责，应先说明拆分建议，再继续实现。
- 当项目结构、模块职责或重要约定发生变动时，必须及时更新本文件。
- 所有新 AI 通讯入口必须优先复用 `data/ai` 统一管线，并按“系统提示词 -> 人设提示词 -> 输出要求 -> JSON结构 -> 历史记录 -> 状态卡 -> 用户最新的发言”的顺序组装请求。
- 时间跳过期间的线上行为必须由 `TimeSkipEngine` 的统一场景一次性处理，覆盖私聊消息、朋友圈动态与推特动态；严禁再为推特或朋友圈新增独立的时间跳过 AI 请求。
- **保持极致沉浸感文案规范**：在所有面向用户的 UI 界面、文案、交互提示乃至代码的常规业务注释中，**绝对禁止**使用“虚拟图片”、“模拟图片”、“虚拟时间”、“模拟时间”、“虚拟状态栏”等破坏现实手机系统浸入感的技术性词汇。应直接视其为现实存在的载体进行描述（如直接称为“照片”、“图片”、“系统时间”、“状态栏”），以维护浑然一体的世界观和真实的拟真操作质感。

## 主题与自适应开发指南

后续开发新应用（如“档案”、“聊天”、“日记”等子屏幕）时，为了确保完美支持深色 / 浅色主题切换，必须遵循以下开发约定：

1. **绝对禁止硬编码色值：**
   - 不要在 UI 组件中硬编码静态色彩（如直接引用 `StarWhite`、`SpaceDeepBlack` 等），这会导致在另一个主题下字迹模糊或背景不匹配。
   - 所有普通 UI 控件的颜色（文字、卡片背景、输入框边框、分割线等）必须绑定到 `MaterialTheme.colorScheme` 的语义色（例如 `onBackground`、`onSurface`、`surfaceVariant` , `outline` 等）。

2. **读取与切换全局主题：**
   - 使用 `LocalThemeConfig.current` 获取当前全局的主题上下文：
     - `val isDark = LocalThemeConfig.current.isDark`：读取当前是否为深色模式。
     - `LocalThemeConfig.current.setDarkTheme(isDark = false)`：在任何应用内调用该函数即可触发全局瞬间应用浅色 / 深色主题，并由底层持久化存储。

3. **桌面组件及对比度自适应：**
   - 凡是直接渲染在桌面壁纸之上的文本或微粒（如时钟、App 标签字、壁纸微粒、状态栏图标）：
     - 需使用 `LocalThemeConfig.current.isDark` 预先判别，进而在深浅不同壁纸底色上动态渲染高可读性颜色（例如浅色模式下应用深色文字 `LightTextPrimary`，深色模式下应用浅色文字 `StarWhite`），从而维护极致的高级视觉体验。

4. **美术与动效风格准则：**
   - 本项目的美术风格致力于**简洁易用**。一般情况下**使用纯色（Solid Color）而非渐变色（Gradient Color）**。
   - 除特定系统级核心交互反馈外，避免添加繁冗、晃眼的多余呼吸或循环缩放动画，保持极致的极简扁平化现代感。
