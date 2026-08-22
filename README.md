# NetBridge — простой Android VPN-клиент (VLESS, подписка)

Минималистичный клиент: один экран, список серверов из ссылки-подписки,
кнопка подключения, привязка устройства (device id / HWID) к подписке для
ограничения числа устройств.

## Статус

| Модуль | Статус |
|---|---|
| UI (главный экран, карточка подписки, список серверов, свайп-жесты) | ✅ |
| Парсинг `vless://` подписки (base64 + обычный список) | ✅ |
| Метаданные подписки (название/трафик/срок/объявление) из заголовков ответа | ✅ |
| Device ID (HWID) в заголовке `X-HWID` при запросе подписки | ✅ |
| `VpnService`, foreground-уведомление, жизненный цикл | ✅ |
| **Сам туннель (Xray-core, реальная передача трафика)** | ✅ |

Проверено вживую: при подключении внешний IP устройства меняется на IP
сервера подписки; при отключении возвращается обратно.

## Как работает туннель

Протокол VLESS/Reality/XHTTP реализован в Xray-core (Go) — готового
Maven/JitPack-пакета для Android нет, поэтому `app/libs/libv2ray.aar`
собирается напрямую из [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite)
через `gomobile bind` (скрипт: [`scripts/build_xray_aar.sh`](scripts/build_xray_aar.sh)).
Собранный AAR закоммичен в репозиторий — пересобирать нужно только при
обновлении Xray-core.

У Xray-core есть встроенный inbound-тип `tun`, который читает/пишет уже
открытый TUN-дескриптор напрямую через userspace-стек на gVisor
(`proxy/tun/tun_android.go` в xray-core) — отдельный tun2socks не нужен:

1. [`CoreVpnService`](app/src/main/java/com/netbridge/app/vpn/CoreVpnService.kt)
   создаёт TUN-интерфейс через `VpnService.Builder` и **исключает из VPN
   собственное приложение** (`addDisallowedApplication(packageName)`) —
   поскольку Xray-core работает как Go-библиотека в том же процессе, а не
   отдельным процессом, это заменяет привычный `protect(fd)` на сокет: весь
   исходящий трафик самого приложения (включая соединение Xray → реальный
   VLESS-сервер) идёт в обход туннеля целиком, без обёртки каждого сокета.
2. [`VlessTunnelConfigBuilder`](app/src/main/java/com/netbridge/app/vpn/VlessTunnelConfigBuilder.kt)
   собирает JSON-конфиг Xray: inbound `tun`, outbound `vless` (tcp / ws / grpc
   / xhttp, tls / reality).
3. [`XrayTunnelEngine`](app/src/main/java/com/netbridge/app/vpn/XrayTunnelEngine.kt)
   вызывает `Libv2ray.newCoreController(...)` → `controller.startLoop(json, tunFd)`.

`StubTunnelEngine` остаётся в коде как безопасный fallback: если
`app/libs/libv2ray.aar` отсутствует при сборке, приложение честно покажет
ошибку вместо того, чтобы притворяться подключённым.

### Пересборка `libv2ray.aar`

Нужно: Go 1.21+, Android SDK + NDK.

```bash
export ANDROID_HOME=/path/to/Android/Sdk
./scripts/build_xray_aar.sh
```

## HWID / ограничение числа устройств

Настоящего аппаратного ID Android не отдаёт приложениям без спецразрешений.
`DeviceIdentity.kt` использует `Settings.Secure.ANDROID_ID` в 16-символьном
hex-формате (как у Happ/v2RayTun — подтверждено перехватом трафика реального
клиента через PCAPdroid), с fallback на случайный hex для редких битых
устройств.

Этот ID отправляется при каждом запросе подписки заголовком **`X-HWID`**
(подтверждённое требование живой Remnawave-панели — простой query-параметр
`hwid`/`device_id` не срабатывал, панель отдавала подписку с
единственным сервером-плейсхолдером без правильного заголовка).

**Ограничение количества устройств — задача бэкенда** (панели, откуда
раздаётся подписка). Приложение только передаёт идентификатор; серверная
сторона должна считать уникальные `X-HWID` на ключ и решать, отдавать ли
реальный список серверов.

## О "невидимости" VPN для других приложений

Android **обязан** показывать постоянное уведомление и иконку-ключ в
статус-баре, пока активен `VpnService` — это встроенная в ОС гарантия
прозрачности для владельца устройства, её нельзя убрать без root/эксплойтов,
и в этом приложении такого кода нет и не будет.

То, что реально можно и сделано: приложение не называется "VPN" и не
использует характерную иконку — как и у любого обычного коммерческого
VPN-клиента в Google Play.

Что специально не делается: обход проверок `ConnectivityManager` /
`NetworkCapabilities.TRANSPORT_VPN`, которые сторонние приложения (например,
антифрод в банковских приложениях) используют осознанно — это уже не
"приватность", а обход чужой защиты.

## Сборка и запуск

```bash
./gradlew assembleDebug
```

Требования: JDK 17, Android SDK (compileSdk 36). Открыть в Android Studio —
самый простой способ получить корректно настроенное окружение и запустить
на эмуляторе/устройстве.

## Структура проекта

```
app/src/main/java/com/netbridge/app/
  MainActivity.kt              — единственный экран
  model/VlessConfig.kt         — распарсенный vless:// сервер
  subscription/                — загрузка, парсинг подписки, метаданные, DoH-фолбэк
  device/DeviceIdentity.kt     — HWID
  store/AppPreferences.kt      — персистентное состояние
  ui/                          — анимации кнопки, свайп-жесты серверов, пинг
  vpn/
    TunnelEngine.kt            — интерфейс движка
    XrayTunnelEngine.kt        — реальный движок (libv2ray.aar / Xray-core)
    StubTunnelEngine.kt        — fallback, если AAR не собран
    VlessTunnelConfigBuilder.kt— VlessConfig -> Xray JSON (tun inbound)
    CoreVpnService.kt          — android.net.VpnService
    TunnelController.kt        — VPN-consent + старт/стоп сервиса
    TunnelStatus.kt            — состояние для UI
scripts/build_xray_aar.sh      — пересборка libv2ray.aar из AndroidLibXrayLite
```
