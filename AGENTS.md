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
│       │   │   │       └── AiProfile.kt
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
│       │   │       │   └── SettingsMainScreen.kt
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

- `app/src/main/java/com/moonlib/cosmos/MainActivity.kt`：应用入口 Activity。
- `app/src/main/java/com/moonlib/cosmos/ui/desktop`：桌面主界面相关 UI 组件。
- `app/src/main/java/com/moonlib/cosmos/ui/settings`：设置界面与模型服务配置相关 UI。
- `app/src/main/java/com/moonlib/cosmos/ui/theme`：Compose 主题、颜色与字体定义。
- `app/src/main/java/com/moonlib/cosmos/data/settings`：AI 配置与设置数据访问逻辑。
- `app/src/main/res`：Android 资源文件，包括图标、主题、字符串、颜色、备份与数据提取规则。
- `app/src/test`：本地单元测试。
- `app/src/androidTest`：Android 仪器测试。
- `gradle`：Gradle Wrapper 与版本目录配置。

## 维护约定

- 新增功能时优先放入与职责匹配的包中；如果没有合适位置，应先创建清晰的新包或模块边界。
- UI 组件、数据模型、数据访问、业务逻辑应保持分离，避免在单个 Composable 或 Activity 中堆叠过多职责。
- 修改现有文件前，如果发现文件已经承担多个职责，应先说明拆分建议，再继续实现。
- 当项目结构、模块职责或重要约定发生变动时，必须及时更新本文件。
