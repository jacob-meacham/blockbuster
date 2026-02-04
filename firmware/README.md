# Blockbuster NFC Reader - ESP32 Firmware

ESP32-based NFC reader that triggers media playback on the Blockbuster server. Tap a cartridge containing an NFC tag, press the button, and your movie/show/music starts playing.

## Hardware

- ESP32 DevKit (any variant)
- PN532 NFC reader module (I2C mode)
- WS2812B / Neopixel RGB LED (single)
- Momentary push button

## Wiring

| ESP32 Pin | Component | Pin |
|-----------|-----------|-----|
| GPIO 21 (SDA) | PN532 | SDA |
| GPIO 22 (SCL) | PN532 | SCL |
| 3.3V | PN532 | VCC |
| GND | PN532 | GND |
| GPIO 27 | WS2812B | DIN |
| 5V | WS2812B | VCC |
| GND | WS2812B | GND |
| GPIO 13 | Button | One leg |
| GND | Button | Other leg |

Pin assignments can be changed in `include/config.h`.

**PN532 I2C mode**: Set the DIP switches on the PN532 module to I2C (typically: switch 1 = ON, switch 2 = OFF). Consult your module's documentation.

## Building & Flashing

Requires [PlatformIO](https://platformio.org/).

```bash
cd firmware

# Build
pio run

# Flash
pio run -t upload

# Monitor serial output
pio device monitor
```

## First-Time Setup

1. Flash the firmware to the ESP32
2. The LED will pulse purple (no WiFi configured)
3. Connect your phone/laptop to the **"Blockbuster-Setup"** WiFi network
4. A captive portal will open automatically (or navigate to 192.168.4.1)
5. Configure:
   - **WiFi network** and password
   - **Device ID** (e.g., `living-room`) - sent to server for theater setup
   - **Server URL** (optional) - override the host in NFC tag URLs (e.g., `http://192.168.1.100:8584`)
6. Save - the ESP32 will connect to WiFi and the LED turns solid blue

Configuration is saved to flash and persists across reboots. To reconfigure, the captive portal reappears automatically if WiFi connection fails.

## LED Status

| Color | Pattern | Meaning |
|-------|---------|---------|
| Blue | Solid | Ready - waiting for button press |
| Purple | Breathing | No WiFi / config portal active |
| Yellow | Solid | Reading tag / sending request |
| Green | Solid (2s) | Playback started successfully |
| Red | Solid (2s) | Error (no tag, HTTP failure, etc.) |
| Red | Fast blink | PN532 not detected (check wiring) |

## NFC Tags

Tags should contain an NDEF URI record with the full play URL:

```
http://your-server:8584/play/{uuid}
```

Standard NFC tools (NFC Tools app, nfcpy, etc.) can write NDEF URL records to NTAG213/215/216 tags.

If a **Server URL** is configured on the device, the host portion of the tag URL is replaced with the configured server, allowing tags to work across different network setups.

## Troubleshooting

**LED stays purple**: WiFi credentials are wrong or network is unreachable. The config portal should appear - reconnect to "Blockbuster-Setup" AP.

**Red blink on startup**: PN532 not detected. Check I2C wiring (SDA/SCL) and that the module's DIP switches are set to I2C mode.

**Button press does nothing**: Ensure an NFC tag is placed directly on the PN532 reader. The tag must contain an NDEF URI record.

**Yellow then red**: Tag was read but the HTTP request failed. Check that the server is running and reachable. Monitor serial output (`pio device monitor`) for details.
