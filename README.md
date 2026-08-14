# NetBridge — простой Android VPN-клиент (VLESS, подписка)

Прототип минималистичного клиента: один экран, список серверов из ссылки-подписки,
кнопка подключения, привязка устройства (device id) к подписке для ограничения
числа устройств.

## Статус

Это **первый экземпляр** (скелет проекта), не готовый production-релиз:

| Модуль | Статус |
|---|---|
| UI (главный экран, список серверов, диалог подписки) | ✅ реализовано |
| Парсинг `vless://` подписки (base64 + обычный список) | ✅ реализовано |
| Device ID (HWID-аналог) и его отправка при запросе подписки | ✅ реализовано |
| `VpnService`, foreground-уведомление, жизненный цикл | ✅ реализовано |
| Сборка JSON-конфига Xray из `VlessConfig` | ✅ реализовано (`VlessTunnelConfigBuilder`) |
| **Сам туннель (передача пакетов, протокол VLESS)** | ⛔ не реализовано — см. ниже |

Кнопка "Подключить" сейчас честно покажет ошибку
`error_engine_missing`, а не притворится, что трафик пошёл через VPN.

## Почему движок не готов "из коробки"

Протокол VLESS реализован только в Xray-core (Go). Официального
Maven/JitPack-пакета для Android нет — даже эталонный клиент
[v2rayNG](https://github.com/2dust/v2rayNG) сам собирает `libv2ray.aar` из
исходников [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)
через Go + `gomobile bind` на этапе CI. В этой сессии не было тулчейна
Go/gomobile и Android NDK, поэтому AAR негде было собрать и проверить.

## Building the tunnel engine (что сделать дальше)

1. Установите Go 1.21+ и `gomobile`:
   ```bash
   go install golang.org/x/mobile/cmd/gomobile@latest
   gomobile init
   ```
2. Склонируйте `AndroidLibXrayLite` и соберите AAR:
   ```bash
   git clone https://github.com/2dust/AndroidLibXrayLite
   cd AndroidLibXrayLite
   gomobile bind -androidapi 24 -target=android -o libv2ray.aar ./
   ```
3. Положите `libv2ray.aar` в `app/libs/` этого проекта (подключается
   автоматически через `fileTree` в `app/build.gradle.kts`).
4. Откройте проект в Android Studio — теперь IDE видит реальные классы
   `libv2ray.*`, и можно с автодополнением дописать
   [`app/src/main/java/com/netbridge/app/vpn/XrayTunnelEngine.kt`](app/src/main/java/com/netbridge/app/vpn/XrayTunnelEngine.kt)
   по шагам, описанным в kdoc этого файла (JSON-конфиг уже готов в
   `VlessTunnelConfigBuilder`, нужно только создать Xray point, прокинуть
   `protect()` и связать TUN-дескриптор с локальным SOCKS через tun2socks).
5. В `CoreVpnService.kt` заменить `StubTunnelEngine()` на `XrayTunnelEngine()`.
6. Тестировать **обязательно на реальном устройстве или эмуляторе** —
   `VpnService` нельзя проверить без Android-рантайма.

## HWID / ограничение числа устройств

Настоящего аппаратного ID Android не отдаёт приложениям без спецразрешений.
`DeviceIdentity.kt` использует `Settings.Secure.ANDROID_ID` (стабилен на
устройство+приложение+пользователя, переживает переустановку, сбрасывается
только при заводском сбросе), с fallback на случайный UUID для редких
битых устройств.

Этот ID отправляется при каждом запросе подписки:
- заголовком `X-Device-Id`
- и query-параметром `?device_id=...`

**Ограничение количества устройств — задача бэкенда** (панели, откуда
раздаётся подписка: 3x-ui, Marzban, самописная). Приложение только передаёт
идентификатор; серверная сторона должна:
1. Логировать `device_id` при каждом запросе подписки по ключу.
2. Считать количество уникальных `device_id` за последние N дней на ключ.
3. Отдавать пустой список / 403, если лимit устройств превышен.

Ничего из этого не реализовано на сервере в рамках данного репозитория —
это конфигурация конкретной панели, не Android-кода.

## О "невидимости" VPN для других приложений

Android **обязан** показывать постоянное уведомление и иконку-ключ в
статус-баре, пока активен `VpnService` — это встроенная в ОС гарантия
прозрачности для владельца устройства, её нельзя убрать без root/эксплойтов,
и в этом прототипе такого кода нет и не будет.

То, что реально можно и сделано:
- приложение не называется "VPN" и не использует характерную иконку —
  как и у любого обычного коммерческого VPN-клиента в Google Play.

Что специально не делается: обход проверок `ConnectivityManager` /
`NetworkCapabilities.TRANSPORT_VPN`, которые сторонние приложения (например,
антифрод в банковских приложениях) используют осознанно — это уже не
"приватность", а обход чужой защиты.

## Сборка и запуск

```bash
./gradlew assembleDebug
```

Требования: JDK 17, Android SDK (compileSdk 34). Открыть в Android Studio —
самый простой способ получить корректно настроенное окружение и запустить
на эмуляторе/устройстве.

## Структура проекта

```
app/src/main/java/com/netbridge/app/
  MainActivity.kt              — единственный экран
  model/VlessConfig.kt         — распарсенный vless:// сервер
  subscription/                — загрузка и парсинг подписки
  device/DeviceIdentity.kt     — HWID-аналог
  store/AppPreferences.kt      — единственное персистентное состояние
  vpn/
    TunnelEngine.kt            — интерфейс движка
    StubTunnelEngine.kt        — используется, пока нет Xray AAR
    XrayTunnelEngine.kt        — заготовка для реальной интеграции
    VlessTunnelConfigBuilder.kt— VlessConfig -> Xray JSON
    CoreVpnService.kt          — android.net.VpnService
    TunnelController.kt        — VPN-consent + старт/стоп сервиса
    TunnelStatus.kt            — состояние для UI
```
