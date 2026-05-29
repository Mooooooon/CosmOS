# AGENTS.md

## 规则

1. 使用中文沟通。
2. 每一个类、文件或模块应当仅负责一项明确的职责。
3. 当检测到某个文件过于臃肿时，必须主动提出重构和拆分方案，严禁继续往该文件中追加新逻辑。

## 项目结构

```text
E:\Android\CosmOS
├── .gitignore
├── AGENTS.md
├── README.md
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── local.properties
├── settings.gradle.kts
├── app
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src
│       ├── androidTest
│       │   └── java\com\moonlib\cosmos\ExampleInstrumentedTest.kt
│       ├── main
│       │   ├── AndroidManifest.xml
│       │   ├── java\com\moonlib\cosmos
│       │   │   ├── MainActivity.kt
│       │   │   ├── data
│       │   │   │   └── settings
│       │   │   │       ├── AiConfigRepository.kt
│       │   │   │       ├── AiProfile.kt
│       │   │   │       └── ThemeSettingsRepository.kt
│       │   │   └── ui
│       │   │       ├── desktop
│       │   │       │   ├── AppGrid.kt
│       │   │       │   ├── AppIconItem.kt
│       │   │       │   ├── DesktopApp.kt
│       │   │       │   ├── DesktopClock.kt
│       │   │       │   ├── DesktopScreen.kt
│       │   │       │   └── VirtualStatusBar.kt
│       │   │       ├── settings
│       │   │       │   ├── ModelServiceConfigScreen.kt
│       │   │       │   ├── ModelServicesListScreen.kt
│       │   │       │   ├── SettingsAppScreen.kt
│       │   │       │   ├── SettingsMainScreen.kt
│       │   │       │   └── ThemeSettingsScreen.kt
│       │   │       └── theme
│       │   │           ├── Color.kt
│       │   │           ├── Theme.kt
│       │   │           └── Type.kt
│       │   └── res
│       │       ├── drawable
│       │       ├── mipmap-anydpi
│       │       ├── mipmap-hdpi
│       │       ├── mipmap-mdpi
│       │       ├── mipmap-xhdpi
│       │       ├── mipmap-xxhdpi
│       │       ├── mipmap-xxxhdpi
│       │       ├── values
│       │       └── xml
│       └── test
│           └── java\com\moonlib\cosmos\ExampleUnitTest.kt
└── gradle
    ├── gradle-daemon-jvm.properties
    ├── libs.versions.toml
    └── wrapper
        ├── gradle-wrapper.jar
        └── gradle-wrapper.properties
```

## 结构说明

- `app/src/main/java/com/moonlib/cosmos/MainActivity.kt`：应用入口 Activity，全局主题注入处。
- `app/src/main/java/com/moonlib/cosmos/ui/desktop`：桌面主界面相关 UI 组件与自适应壁纸。
- `app/src/main/java/com/moonlib/cosmos/ui/settings`：设置界面、模型服务配置与个性化主题配置。
- `app/src/main/java/com/moonlib/cosmos/ui/theme`：Compose 主题、配色集（深浅双色板）与 CompositionLocal 定义。
- `app/src/main/java/com/moonlib/cosmos/data/settings`：AI 配置仓库与主题配置持久化仓库。
- `app/src/main/res`：Android 资源文件。
- `app/src/test`：本地单元测试。
- `app/src/androidTest`：Android 仪器测试。
- `gradle`：Gradle Wrapper 与版本目录配置。

## 维护约定

- 新增功能时优先放入与职责匹配的包中；如果没有合适位置，应先创建清晰的新包或模块边界。
- UI 组件、数据模型、数据访问、业务逻辑应保持分离，避免在单个 Composable 或 Activity 中堆叠过多职责。
- 修改现有文件前，如果发现文件已经承担多个职责，应先说明拆分建议，再继续实现。
- 当项目结构、模块职责或重要约定发生变动时，必须及时更新本文件。

## 主题与自适应开发指南

后续开发新应用（如“档案”、“聊天”、“日记”等子屏幕）时，为了确保完美支持深色 / 浅色主题切换，必须遵循以下开发约定：

1. **绝对禁止硬编码色值：**
   - 不要在 UI 组件中硬编码静态色彩（如直接引用 `StarWhite`、`SpaceDeepBlack` 等），这会导致在另一个主题下字迹模糊或背景不匹配。
   - 所有普通 UI 控件的颜色（文字、卡片背景、输入框边框、分割线等）必须绑定到 `MaterialTheme.colorScheme` 的语义色（例如 `onBackground`、`onSurface`、`surfaceVariant`、`outline` 等）。

2. **读取与切换全局主题：**
   - 使用 `LocalThemeConfig.current` 获取当前全局的主题上下文：
     - `val isDark = LocalThemeConfig.current.isDark`：读取当前是否为深色模式。
     - `LocalThemeConfig.current.setDarkTheme(isDark = false)`：在任何应用内调用该函数即可触发全局瞬间应用浅色 / 深色主题，并由底层持久化存储。

3. **桌面组件及对比度自适应：**
   - 凡是直接渲染在桌面壁纸之上的文本或微粒（如时钟、App 标签字、壁纸微粒、状态栏图标）：
     - 需使用 `LocalThemeConfig.current.isDark` 预先判别，进而在深浅不同壁纸底色上动态渲染高可读性颜色（例如浅色模式下应用深色文字 `LightTextPrimary`，深色模式下应用浅色文字 `StarWhite`），从而维护极致的高级视觉体验。

