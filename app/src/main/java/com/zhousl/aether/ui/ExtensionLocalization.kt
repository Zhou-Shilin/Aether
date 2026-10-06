package com.zhousl.aether.ui

import com.zhousl.aether.data.AppLanguage
import androidx.compose.runtime.staticCompositionLocalOf

/** Supplies the language selected by the Android settings to native extension UI. */
internal val LocalAetherLanguage = staticCompositionLocalOf { AppLanguage.English }

/** Translates the two builtin composer entries only for Russian and preserves other extension titles. */
internal fun extensionComposerMenuTitle(localId: String, title: String, language: AppLanguage): String =
    if (language == AppLanguage.Russian) {
        when (localId) {
            "subagents" -> "Субагенты"
            "research-web" -> "Исследование в интернете"
            else -> title
        }
    } else {
        title
    }


/** Translates known extension display text for Russian, preserving unknown text and interpolated error details. */
internal fun extensionText(value: String, language: AppLanguage): String {
    if (language != AppLanguage.Russian) return value
    val prefix = "Web Access settings could not read the Pi config: "
    if (value.startsWith(prefix)) {
        return "Настройки веб-доступа: не удалось прочитать конфигурацию Pi: " + value.removePrefix(prefix)
    }
    val providerPrefix = "Default provider and "
    val providerSuffix = " configuration. Credentials are persisted in the existing Pi config file."
    if (value.startsWith(providerPrefix) && value.endsWith(providerSuffix)) {
        val provider = value.removePrefix(providerPrefix).removeSuffix(providerSuffix)
        return "Настройка провайдера и раздела $provider. Данные доступа сохраняются в существующем файле конфигурации Pi."
    }
    return russianExtensionText(value)
}

/**
 * Matches builtin display strings exactly, leaving custom extension text unchanged.
 * Upstream label changes require updating this table; stable translation IDs would
 * require a separate extension API change.
 */
private fun russianExtensionText(value: String): String = when (value) {
    "Web Access" -> "Веб-доступ"
    "Search, source verification, extraction, and provider routing" -> "Поиск, проверка источников, извлечение данных и маршрутизация провайдеров"
    "Web search tools" -> "Инструменты веб-поиска"
    "Register search and source-check tools after the next extension reload." -> "Зарегистрировать инструменты поиска и проверки источников после следующей перезагрузки расширений."
    "Provider" -> "Провайдер"
    "Default search provider, credentials, and base URLs" -> "Провайдер поиска по умолчанию, учётные данные и базовые URL"
    "Context Extraction" -> "Извлечение контекста"
    "GitHub, video, and PDF handling" -> "Работа с GitHub, видео и PDF"
    "Privacy and network" -> "Конфиденциальность и сеть"
    "Browser data access, SSRF exceptions, and fetch domain policy" -> "Доступ к данным браузера, исключения SSRF и правила доменов загрузки"
    "MCP Servers" -> "MCP-серверы"
    "Manage MCP servers, inspect each transport config, and keep only the connections you want active." -> "Управляйте MCP-серверами, проверяйте настройки транспорта и оставляйте только нужные подключения активными."
    "Subagents" -> "Субагенты"
    "Subagent concurrency, dispatch, persistence, and Aether UI" -> "Параллелизм, распределение задач, сохранение данных и интерфейс Aether для субагентов"
    "Subagent Types" -> "Типы субагентов"
    "Enable, disable, and reload custom agent definitions" -> "Включение, отключение и перезагрузка пользовательских определений агентов"
    "Runtime" -> "Среда выполнения"
    "Agents" -> "Агенты"
    "Models" -> "Модели"
    "Aether and Pi UI" -> "Интерфейс Aether и Pi"
    "Max concurrency" -> "Максимальный параллелизм"
    "Default max turns" -> "Максимум ходов по умолчанию"
    "Grace turns" -> "Дополнительные ходы"
    "Nested depth" -> "Глубина вложенности"
    "Join mode" -> "Режим объединения"
    "Scheduling" -> "Планирование"
    "Output transcript" -> "Сохранять расшифровку"
    "Disable defaults" -> "Отключить стандартных агентов"
    "Fallback agent" -> "Резервный агент"
    "Strict agent files" -> "Строгая проверка файлов агентов"
    "Tool description" -> "Описание инструментов"
    "Scope models" -> "Ограничить модели"
    "Widget" -> "Виджет"
    "Fleet view" -> "Вид флота"
    "Agent mentions" -> "Упоминания агентов"
    "Remember agents" -> "Запоминать агентов"
    "Manage" -> "Управление"
    "Create agent definition" -> "Создать определение агента"
    "Reload agent files" -> "Перезагрузить файлы агентов"
    "MCP Runtime" -> "Среда выполнения MCP"
    "No MCP servers" -> "Нет MCP-серверов"
    "Add HTTP or stdio servers to extend capabilities." -> "Добавьте HTTP- или stdio-серверы для расширения возможностей."
    "Add server" -> "Добавить сервер"
    "Add MCP server" -> "Добавить MCP-сервер"
    "New MCP server" -> "Новый MCP-сервер"
    "Configuration" -> "Конфигурация"
    "Actions" -> "Действия"
    "Configured Servers" -> "Настроенные серверы"
    "Tap any server to view details, inspect available tools, or reconnect." -> "Нажмите на сервер, чтобы просмотреть сведения, инструменты или подключиться заново."
    "Runtime and OAuth" -> "Среда выполнения и OAuth"
    "Maximum concurrent background agents. Queued agents start as slots free." -> "Максимальное число фоновых агентов. Ожидающие агенты запускаются по мере освобождения мест."
    "Default maximum agentic turns before wrap-up. 0 means unlimited." -> "Максимальное число ходов агента до завершения. 0 означает без ограничений."
    "Additional turns after the wrap-up steering message." -> "Дополнительные ходы после сообщения о завершении."
    "Hard cap on nested delegation. Main is 0; 0 or 1 disables nesting." -> "Ограничение вложенного делегирования. Основной агент имеет уровень 0; 0 или 1 отключает вложенность."
    "Default completion grouping for background agents." -> "Группировка завершения фоновых агентов по умолчанию."
    "Enable the schedule parameter and scheduled-job menu. Tool-spec changes apply on the next Pi session." -> "Включить параметр расписания и меню запланированных задач. Изменения применятся в следующем сеансе Pi."
    "Write each subagent .output transcript by default. Agent frontmatter can override this." -> "По умолчанию сохранять расшифровку .output каждого субагента. Настройки агента могут изменить это."
    "Hide built-in general-purpose, Explore, and Plan agents. Custom agents are unaffected." -> "Скрыть встроенных агентов общего назначения, Explore и Plan. Пользовательские агенты не затрагиваются."
    "Agent used when subagent_type is unknown, disabled, or ambiguous. none rejects the call instead." -> "Агент, используемый при неизвестном, отключённом или неоднозначном типе. none отклоняет вызов."
    "Fail startup on an unreadable or unparseable agent .md file instead of skipping it." -> "Останавливать запуск при нечитаемом или некорректном файле агента .md вместо его пропуска."
    "Agent tool description size/mode. Custom reads .pi/agent-tool-description.md." -> "Размер и режим описания инструментов агента. Custom читает .pi/agent-tool-description.md."
    "Validate subagent model choices against Pi scoped models (/scoped-models)." -> "Проверять выбор моделей субагентов по моделям Pi из /scoped-models."
    "Live Aether agent card visibility above the composer." -> "Показывать карточку активного агента Aether над полем ввода."
    "Keep the TUI FleetView enabled for Pi CLI; Aether renders the same roster as tappable cards." -> "Сохранять FleetView TUI для Pi CLI; Aether показывает тот же список в виде карточек."
    "Route @handle messages to that agent. Model starts new agents through an off-screen clone; direct starts them immediately." -> "Направлять сообщения @handle выбранному агенту. Режим Model запускает агентов через скрытую копию, Direct — сразу."
    "Persist subagent sessions so @handle can resume them long after completion." -> "Сохранять сеансы субагентов, чтобы @handle мог возобновить их после завершения."
    "Subagent complete" -> "Субагент завершил работу"
    "Subagent result" -> "Результат субагента"
    "Subagent conversation" -> "Диалог с субагентом"
    "Agent types" -> "Типы агентов"
    "Enable or disable individual agent types. Disabled built-ins get a project stub; disabled custom agents keep their file." -> "Включайте и отключайте отдельные типы агентов. Для отключённых встроенных агентов создаётся заглушка проекта; файлы пользовательских агентов сохраняются."
    "Draft a subagent creation request" -> "Создать запрос на добавление субагента"
    "Default search provider" -> "Провайдер поиска по умолчанию"
    "Used whenever a tool call leaves provider on Auto." -> "Используется, если для вызова инструмента выбран режим «Авто»."
    "Comma-separated providers in priority order. Saving a route clears the single-provider override and uses transient, quota, and network fallbacks by default." -> "Провайдеры через запятую в порядке приоритета. Сохранение маршрута отключает одиночного провайдера и включает резервные варианты по сбоям, квотам и сети."
    "Review a draft, return an automatic summary, or return raw results." -> "Проверять черновик, возвращать автоматическую сводку или исходные результаты."
    "Seconds before an idle review is submitted automatically." -> "Сколько секунд ждать перед автоматической отправкой проверки."
    "Open the review UI when a local search starts." -> "Открывать интерфейс проверки при начале локального поиска."
    "Pick a specific provider above to configure its API key, base URL, and provider-specific options." -> "Выберите провайдера выше, чтобы настроить API-ключ, базовый URL и дополнительные параметры."
    "Master switch for web search and source verification" -> "Главный переключатель веб-поиска и проверки источников"
    "Repository cloning, cache path, and size limits" -> "Клонирование репозиториев, путь к кэшу и ограничения размера"
    "Transcript and video understanding for YouTube and local files" -> "Расшифровка и анализ видео YouTube и локальных файлов"
    "PDF download limits" -> "Ограничения загрузки PDF"
    "SSRF exceptions and fetch domain policy" -> "Исключения SSRF и правила доменов загрузки"
    "Advanced" -> "Дополнительно"
    "Routing, review workflow, and summary model" -> "Маршрутизация, проверка результатов и модель сводки"
    "Web research" -> "Исследование в интернете"
    "Web content ready" -> "Веб-содержимое готово"
    "Web access error" -> "Ошибка веб-доступа"
    "Search workflow" -> "Процесс поиска"
    "Search workflow updated" -> "Процесс поиска обновлён"
    "Gemini Web account" -> "Учётная запись Gemini Web"
    "Web Access setting saved. Reload the Pi extension to apply it." -> "Настройки веб-доступа сохранены. Перезагрузите расширение Pi, чтобы применить изменения."
    "Draft a multi-source research request" -> "Создание запроса для исследования по нескольким источникам"
    else -> value
}
