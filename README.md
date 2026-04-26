# PaykeyfearVPN

Android VPN-клиент с поддержкой трёх современных протоколов обхода блокировок.

## Протоколы

| Протокол | Описание |
|----------|----------|
| **AmneziaWG** | Обфусцированный WireGuard от Amnezia. Маскирует трафик под случайные данные, обходит DPI |
| **VLESS** | Прокси-протокол на базе Xray-core. Лёгкий, без лишнего шифрования поверх TLS |
| **Hysteria2** | Протокол на базе QUIC (UDP). Высокая скорость на плохих каналах, устойчив к потере пакетов |

## Возможности

- Импорт конфигов через URI-ссылки (`vless://`, `hysteria2://`, `wireguard://`) или файлы (`.conf`, `.vpn`, YAML)
- Раздельное туннелирование — выбор конкретных приложений для VPN
- Always-on VPN через системные настройки Android
- Connect on boot — автоподключение после перезагрузки
- Переключение языка интерфейса (русский / английский / системный)
- Минимальный интерфейс без рекламы и аналитики

## Скачать

Актуальный APK — в разделе [Releases](../../releases/latest).

## Установка

1. Скачай APK из Releases
2. Разреши установку из неизвестных источников в настройках Android
3. Установи и открой приложение
4. Добавь конфигурацию через кнопку «+» на экране серверов
5. Нажми «Подключить»

## Форматы конфигов

| Формат | Пример |
|--------|--------|
| VLESS URI | `vless://uuid@host:443?...` |
| Hysteria2 URI | `hysteria2://pass@host:443` |
| Hysteria2 YAML | файл с полями `server:`, `auth:` |
| WireGuard / AmneziaWG | файл `.conf` с секциями `[Interface]` и `[Peer]` |
| AmneziaVPN bundle | `vpn://...` или `.vpn`-файл |

## Системные требования

- Android 8.0 (API 26) и выше
- Архитектуры: arm64-v8a, armeabi-v7a, x86_64

## Сборка из исходников

### Зависимости

| Инструмент | Версия |
|------------|--------|
| JDK | 17 (Temurin) |
| Android SDK | API 35 |
| Android NDK | r26d |
| Go | 1.26+ |
| Gradle | 8.10.2 (wrapper) |

### Сборка

```sh
# Сборка нативных .aar (Go + gomobile)
export ANDROID_NDK_HOME=/path/to/ndk
./scripts/build-native.sh

# Debug APK
./gradlew :app:assembleDebug

# Release APK (нужен keystore)
./scripts/generate-keystore.sh
./gradlew :app:assembleRelease
```

### Тесты

```sh
./gradlew test                              # JVM unit tests
./gradlew :app:connectedDebugAndroidTest    # Instrumented tests (нужен эмулятор)
./gradlew ktlintCheck detekt :app:lintDebug # Статический анализ
```

## Структура проекта

```
app/                      Compose UI, Hilt DI, навигация
core/                     Доменные модели, абстракции
core-config/              Парсеры конфигураций (AWG, VLESS, Hysteria2, Amnezia)
core-geo/                 GeoIP/GeoSite базы для маршрутизации
vpn-service/              Android VpnService, TunnelController
protocols/awg/            Kotlin-обёртка над нативным AmneziaWG
protocols/vless/          Kotlin-обёртка над нативным VLESS
protocols/hysteria2/      Kotlin-обёртка над нативным Hysteria2
third_party/awg-mobile/         Go-обёртка → awg.aar
third_party/vless-mobile/       Go-обёртка (Xray-core + tun2socks) → vless.aar
third_party/hysteria2-mobile/   Go-обёртка → hysteria.aar
third_party/gomobile-bundle/    Зонтичный модуль, собирает все три в один .aar
```

## CI/CD

GitHub Actions запускается на каждый push и pull request:

- **Android CI** — ktlint, detekt, lint, unit tests, debug APK
- **Native backends** — сборка нативных .aar через gomobile
- **Assemble release** — собирает подписанный APK и AAB, прикрепляет к GitHub Release на тегах `v*`

## Лицензия

GPL-3.0-or-later. Проект включает `amneziawg-go` (GPL-3.0), что распространяет GPL на весь проект.

Полный текст: [LICENSE](LICENSE).
