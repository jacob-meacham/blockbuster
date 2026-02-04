#pragma once

// --- I2C pins for PN532 ---
#define NFC_SDA 21
#define NFC_SCL 22

// --- Button ---
#define BUTTON_PIN 13

// --- WS2812B LED ---
#define LED_PIN 27
#define LED_BRIGHTNESS 64

// --- Timing ---
#define DEBOUNCE_MS 50
#define HTTP_TIMEOUT_MS 10000
#define STATUS_DISPLAY_MS 2000
#define NFC_READ_TIMEOUT_MS 100
#define WIFI_RECONNECT_INTERVAL_MS 30000

// --- NVS ---
#define NVS_NAMESPACE "blockbuster"
#define NVS_KEY_DEVICE_ID "deviceId"
#define NVS_KEY_SERVER_URL "serverUrl"

// --- WiFi AP ---
#define AP_NAME "Blockbuster-Setup"
