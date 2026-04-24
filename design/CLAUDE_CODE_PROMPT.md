# PaykeyfearVPN — Claude Code Handoff Prompt

Скопируй этот промпт в Claude Code:

---

## Промпт

Реализуй редизайн Android-приложения **PaykeyfearVPN** (VPN-клиент, поддерживает VLESS и AmneziaWG/AWG протоколы).

Используй дизайн-мокап из файла `design/PaykeyfearVPN Redesign.html` как точный референс для всех экранов.

---

## Стек и зависимости

- **Язык:** Kotlin + Jetpack Compose
- **Минимальный SDK:** 26
- **Тема:** только тёмная (dark-only)
- **Шрифт:** [Space Grotesk](https://fonts.google.com/specimen/Space+Grotesk) — подключить через Google Fonts / downloadable fonts

---

## Цветовая система (Material 3 / ColorScheme)

```kotlin
val AccentGreen    = Color(0xFF4DDBA8)   // oklch(0.76 0.17 165) — основной акцент
val AccentGreenDim = Color(0xFF1A4D38)   // oklch(0.30 0.10 165) — фон акцентных элементов
val SurfaceBg      = Color(0xFF0D0F1A)   // oklch(0.10 0.015 255) — основной фон
val SurfaceCard    = Color(0xFF161B2E)   // oklch(0.15 0.015 255) — карточки
val SurfaceCard2   = Color(0xFF1C2238)   // oklch(0.19 0.015 255) — приподнятые элементы
val BorderColor    = Color(0xFF252C42)   // oklch(0.22 0.015 255) — разделители/рамки
val TextPrimary    = Color(0xFFF0F2F8)   // oklch(0.95 0.005 250)
val TextMuted      = Color(0xFF6B748A)   // oklch(0.55 0.01 250)
val Blue           = Color(0xFF4D9FE8)   // oklch(0.65 0.18 240) — VLESS badge
val BlueDim        = Color(0xFF162540)   // oklch(0.25 0.10 240)
val AmberColor     = Color(0xFFE8C24D)   // oklch(0.78 0.17 80)  — connecting / medium ping
val DangerColor    = Color(0xFFE8614D)   // oklch(0.65 0.18 25)  — error / high ping
val DangerDim      = Color(0xFF3D1A14)   // oklch(0.22 0.08 25)
val AwgGreen       = Color(0xFF4DD870)   // oklch(0.68 0.16 145) — AmneziaWG badge
```

---

## Навигация

Bottom Navigation Bar с 4 вкладками:
| Tab | Иконка | Label |
|-----|--------|-------|
| Home | house | Home |
| Servers | server/database | Servers |
| Import | download | Import |
| Settings | settings | Settings |

Активная вкладка: акцентный цвет + маленькая полоска сверху иконки (28dp × 3dp, borderRadius=2dp).

---

## Экраны

### 1. Home Screen

**Состояния подключения:**
- `OFF` — серый замок (unlocked), пунктирное кольцо
- `CONNECTING` — жёлтая кнопка + вращающаяся дуга (1 сек оборот), текст «Подключение…», длительность 2 сек
- `ON` — зелёный замок (locked), два пульсирующих кольца, текст «Connected · VLESS»
- `DISCONNECTING` — жёлтая кнопка + вращение, длительность 1.6 сек

**Кнопка подключения:**
- Размер: 148dp (inset: 10dp от внешнего кольца)
- `ON`: `radialGradient(oklch(0.25 0.12 165), oklch(0.14 0.08 165))` + glow тень + accentDim border
- `OFF`: тёмный радиальный градиент + border = BorderColor
- `CONNECTING`: amber градиент + amber тень + анимация `alpha 0.4→1` loop

**Таймер сессии:** показывать `HH:MM:SS` под статусом пока `ON`

**Карточка сервера:**
- Иконка globe в blueDim-круге (36dp, radius 10dp)
- Название жирным, под ним `PROTOCOL · IP` (маскировать последние 2 октета: `138.124.*.*:443`)
- Arrow right

**Статистика трафика** (только в `ON`):
- Градиентная карточка `linear(oklch(0.18 0.06 240), oklch(0.15 0.04 255))`
- ↓ Download (акцент) и ↑ Upload (blue) с разделителем

**Карточка раздельного туннеля:** иконка shield + заголовок + статус + arrow

---

### 2. Servers Screen

**Заголовок:** «Серверы» + кнопка «Пинг» справа

**Кнопка Пинг:**
- При нажатии: кнопка подсвечивается акцентом, иконка вращается, текст «Проверка…»
- Симуляция задержки 700мс + 500мс между серверами
- Результат рандомизируется каждый раз

**Карточка сервера:**
- Radio button (accent когда выбран)
- Название + badge протокола (цвет по протоколу) + маскированный IP
- **Ping индикатор** (справа): 3 вертикальных полоски + значение в мс
  - `< 80 мс` → AccentGreen (3 полоски)
  - `< 180 мс` → AmberColor (2 полоски)
  - `≥ 180 мс` → DangerColor (1 полоска)
  - `null` (измерение) → анимация пульса на серых полосках
- Кнопка удаления (мусорное ведро, DangerColor)
- Нажатие на карточку → **Server Detail Bottom Sheet**

**Легенда** внизу: три цветных точки + подписи

**Empty State:**
- Иконка server в карточке
- «Нет серверов» + описание
- Кнопка «+ Добавить сервер» (градиент акцент)

---

### 3. Import Screen

Контент прибит к нижней части экрана (удобно для большого пальца):

**Заголовок:** «Импорт конфигурации» + подзаголовок протоколов

**3 карточки методов** (горизонтальные, полная ширина):
- Иконка в круглом блоке + заголовок + подсказка + arrow
- При нажатии: подсвечиваются акцентом, фон меняется на `linear(oklch(0.18 0.08 165), oklch(0.14 0.05 200))`, border `AccentGreen`
- Методы: Буфер обмена / Файл (.conf, .json) / QR-код

**Разделитель «ИЛИ ВСТАВЬТЕ ТЕКСТ»**

**TextField:**
- Шрифт monospace
- Placeholder: `vless://... или полную конфигурацию`
- При фокусе: border AccentGreen, фон чуть светлее
- Счётчик символов внизу справа
- Кнопка × очистки вверху справа (появляется при наличии текста)

**Кнопка «Импортировать»:**
- Серая и disabled когда поле пустое
- Градиентная (AccentGreen) с glow-тенью когда есть текст

---

### 4. Settings Screen

Список строк с разделителями:
| Элемент | Тип |
|---------|-----|
| Динамическая тема | Switch (вкл. по умолчанию) |
| Подключаться при загрузке | Switch (выкл.) |
| Раздельный туннель | Arrow |
| Политика конфиденциальности | Arrow |
| Журнал | Arrow |
| О приложении (версия) | Arrow |

---

## Bottom Sheets

### Error Sheet (ошибка подключения)

Появляется когда VPN не смог подключиться.

- Handle (36dp × 4dp, borderRadius 2dp) сверху
- Иконка ⚠️ в DangerDim-круге (56dp) с DangerColor border
- Заголовок «Ошибка подключения»
- Описание + код ошибки в monobox (`connection_timeout · 30s`)
- Кнопка «Повторить попытку» (градиент акцент)
- Кнопка «Выбрать другой сервер» (border danger, DangerDim bg)
- Анимация появления: slide up 300мс `cubic-bezier(0.32, 0.72, 0, 1)`

### Server Detail Sheet

Появляется при нажатии на сервер.

- Handle + кнопка закрытия (×) в правом верхнем углу
- Заголовок: иконка протокола + название + badge
- Поля:
  - Адрес (маскированный)
  - Порт
  - Протокол
  - UUID (маскирован как `550e8400-****-****-****-4400`, кнопка copy → показать/скрыть)
- Кнопка «Подключиться» (градиент, AccentGreen glow)
- Кнопка «Удалить сервер» (DangerDim bg, DangerColor text)

---

## Маскировка IP

Скрывать последние 2 октета IP везде в UI:
```kotlin
fun maskIp(addr: String): String {
    val regex = Regex("""^(\d+\.\d+)\.\d+\.\d+(:\d+)?$""")
    return regex.replace(addr) { mr ->
        "${mr.groupValues[1]}.*.*${mr.groupValues[2]}"
    }
}
```

---

## Иконка приложения

Файл: `design/icon-1024.png`

- Тёмный градиентный фон `(#1a1f35 → #0a0c14)`
- Щит с обводкой AccentGreen
- Замочная скважина внутри щита
- Радиус скругления: 22.7% от размера (Google Play adaptive icon)

---

## Виджет рабочего стола (4×1)

`AppWidgetProvider` для главного экрана Android:
- Иконка замка (подключено/отключено) + цвет кнопки
- Название приложения
- Статус + трафик (↓/↑) когда подключено
- Кнопка «Вкл/Откл» (AccentGreen градиент / серый)

---

## Анимации (важно)

| Элемент | Анимация |
|---------|----------|
| Pulse ring (connected) | scale 1→1.15, alpha 0.6→0, 2.4s, ease-out, infinite |
| Pulse ring 2 | то же, delay 0.6s, scale до 1.28 |
| Connecting arc | rotate 0→360°, 1s, linear, infinite |
| Button glow | alpha 0.4↔1, 1s, ease-in-out, infinite |
| Bottom Sheet | translateY(100%)→0, 300мс, cubic(0.32,0.72,0,1) |
| Backdrop | alpha 0→0.55, 200мс |
| Ping bars (measuring) | scaleY pulse 1→1.3, 1s, stagger 0.2s |

---

## Файлы дизайна

- `design/PaykeyfearVPN Redesign.html` — интерактивный мокап (открой в браузере)
- `design/Splash Screen.html` — анимация загрузки (воспроизведи нативно)
- `design/icon-1024.png` — иконка 1024×1024
