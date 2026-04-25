<p align="center">
  <img src="app/src/main/res/drawable-nodpi/aether_mark.png" width="128" height="128" alt="Aether Logo">
</p>

<h1 align="center">Aether | 扶摇</h1>

<p align="center">
  <strong>Soar with local AI.</strong><br>
  Android 高颜值、本地化的通用 AI Agent
</p>

<p align="center">
  <a href="README.md">English</a> •
  <a href="#-视觉与体验">视觉与体验</a> •
  <a href="#-核心特性">核心特性</a> •
  <a href="#-快速开始">快速开始</a> •
  <a href="#-贡献与参与">贡献与参与</a>
</p>

<p align="center">
  <table>
    <tr>
      <td><img src="public/welcome.jpg" width="280"></td>
      <td><img src="public/agentmode.jpg" width="280"></td>
      <td><img src="public/chat.jpg" width="280"></td>
      <td><img src="public/research.jpg" width="280"></td>
    </tr>
  </table>
</p>

---

## 🌪️ Aether 扶摇

> 鹏之徙于南冥也，水击三千里，抟扶摇而上者九万里，去以六月息者也。

**Aether 扶摇** 致力于为 Android 设备提供现代化的本地 AI Agent 体验。告别臃肿的虚拟机配置与繁杂的终端界面，在保持极简轻量 UI 的同时，提供了极其强大的扩展性与无缝的工具调用体验。

---

## 📱 视觉与体验

Aether 的 UI 和交互大量参考了 ChatGPT、Codex CLI/App、Gemini、Poco Agent 等成熟的优秀应用。每一个动画、每一个交互细节都经过精心打磨，打破“开源项目简陋廉价”的刻板印象。

<p align="center">
  <table>
    <tr>
      <td><img src="public/input_bar.jpg" width="280"></td>
      <td><img src="public/tool_execution.jpg" width="280"></td>
      <td><img src="public/msg_options.jpg" width="280"></td>
    </tr>
  </table>
</p>

---

## ✨ 核心特性

- **超高颜值，丝滑交互**: 凝聚 ChatGPT 等优秀应用的设计精华，打造极简、现代、优雅的界面。
- **完整的 Skill/MCP 支持**: 全量支持 Anthropic Agent Skills 和 Model Context Protocol，轻松连接 Google Search、GitHub、Local Files 等数据源。
- **轻量集成 Termux**: 直接连接 Termux 执行 Bash 指令，不采用厚重的内置 Ubuntu/Apline 虚拟机的方案，更自由、更高效。
- **可插拔的 GUI Agent**: GUI Agent 按需启动，处理无法使用指令的复杂交互。

---

## 🛠️ 技术栈

- **Core**: Kotlin / Jetpack Compose / Coroutines
- **System**: Root / Shizuku API
- **AI**: OpenAI Compatible API / MCP (Model Context Protocol)
- **Tooling**: Termux Integration / WebTools (JSoup, Flexmark)
- **Data**: DataStore / YAML configuration

---

## 🚀 快速开始

### 前置要求
- Android 12 或更高版本
- 可选：已 Root 或已安装 [Shizuku](https://shizuku.rikka.app/)
- 可选：已安装 [Termux](https://termux.dev/)

### 安装步骤
1. 下载最新的 [Release APK](https://github.com/zhousl/Aether/releases)。
2. 根据 Get Started Tour 指引配置。

---

## 🤝 贡献与参与

本项目由一名初三学生在课业之余断断续续开发而成 (⁠ʘ⁠ᴗ⁠ʘ⁠✿⁠)。目前项目仍在积极的迭代和打磨中。如果你喜欢这个项目，欢迎点一个 ⭐ Star，或者提交 PR 与 Issue 一起让 Aether 变得更好！

![Donation](public/donation.jpg)

---

## 特别鸣谢

- OpenAI Codex
- Google Gemini
- [Linux DO 社区](https://linux.do/)

---

## 📄 开源协议

本项目采用 **GPL License 3.0**。

---

<p align="center">
  Built with ❤️ by Shilin "BaimoQilin" Zhou.
</p>
