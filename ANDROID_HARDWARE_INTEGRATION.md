# VYORA Android ↔ ESP32 Hardware Integration

This guide is for the Android app developer integrating the current ESP32 DevKit + nRF24L01 test hardware. The phone communicates with its local ESP32 over USB serial; the ESP32 communicates with the other nodes over radio. The Android app does not access the nRF24L01 directly.

## Current Hardware Test

The repository currently has two sketches:

- `nrf_pa_tx/nrf_pa_tx.ino` is NODEA.
- `nrf_pa_rx/nrf_pa_rx.ino` is NODEB.

Flash one sketch to each ESP32. Connect each board's USB port to a phone using a USB OTG adapter/cable. The DevKit appears to Android as a USB-to-serial device (the exact bridge chip varies by board). Power the radio and ESP32 from the USB connection; keep the other node powered as well.

The current serial settings are **115200 baud, 8 data bits, no parity, 1 stop bit (8N1)**. The test accepts newline-terminated text up to **31 ASCII characters** and returns human-readable lines such as:

```text
ESP32 NRF24 serial bridge - NODEA
Radio ready; listening for NODEB messages.
[sent] hello
[received] reply
```

The app should append `\n` when sending. For now, display `[received] ` lines as incoming text and treat `[sent]` / `[send failed]` as test status. Startup and error lines may also appear. This is a temporary bench-test protocol: it has no user IDs, structured message IDs, fragmentation, routing, or end-to-end delivery acknowledgement. `[sent]` only reflects the current radio write/ack result; it must not be shown as final VYORA delivery.

## Android USB Serial Connection

Use Android USB Host (OTG) mode and a USB serial driver library such as [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android), which supports common CP210x and CH340 USB-to-UART bridges. Follow the library's current Gradle setup and examples rather than hardcoding a VID/PID; DevKit bridge chips differ. Android requires user permission before the app opens a USB device. See the [Android USB Host guide](https://developer.android.com/develop/connectivity/usb/host).

Implement a transport interface such as `RadioTransport` with `connect()`, `disconnect()`, `sendLine(text)`, and an incoming-line callback/flow. Keep USB enumeration, permission prompts, serial I/O, and reconnection in the transport layer; Compose screens should observe state and events, not perform blocking reads or writes. Run serial reads off the main thread. Close the port on detach, permission revocation, or app shutdown, and offer reconnect when the device is attached again.

For the current test, write UTF-8 text followed by `\n`; read bytes continuously and split on `\n` (trim a preceding `\r`). Do not assume one USB read equals one complete line. Handle permission denial, unsupported USB devices, disconnects, and write failures as visible connection states.

## App/Protocol Boundary

Keep the Android app independent of the current debug strings. Put line parsing behind the transport adapter so it can later be replaced with the agreed structured app↔ESP32 protocol. The planned app protocol and the radio packet format are separate interfaces: serial can carry larger structured commands, while each nRF24L01 radio packet is limited to 32 bytes. The ESP32 firmware will need to frame/fragment app messages for radio transmission and reassemble them where required.

In the VYORA design, the app owns user identity, chat storage, UI, and explicit location/SOS sharing. Firmware owns radio operations and should report radio/connection status accurately. A recipient message is **Delivered** only after an end-to-end acknowledgement from the intended recipient; a radio hop acknowledgement is not enough. Avoid logging message text or location in production builds.

## Bench Test Checklist

1. Connect each phone to a different powered ESP32 with OTG.
2. Grant USB access and confirm the app reports the serial connection open.
3. Send `hello` from one phone and confirm the other receives it; reply in the opposite direction.
4. Disconnect/reconnect USB and confirm the app recovers without restarting.
5. Record board model, USB serial bridge chip (if known), Android version, and exact serial output for failures.

The two-node sketches are only a direct-link test. Mesh discovery, multi-hop relay, store-and-forward, and the final app protocol are not implemented yet; validate relay behavior with at least three radio nodes once that firmware exists.
