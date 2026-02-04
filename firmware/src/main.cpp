#include <Arduino.h>
#include <Wire.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <Preferences.h>
#include <WiFiManager.h>
#include <Adafruit_PN532.h>
#include <FastLED.h>

#include "config.h"

// --- LED ---
CRGB leds[1];

enum class LedState { IDLE, NO_WIFI, WORKING, SUCCESS, ERROR };
static LedState ledState = LedState::NO_WIFI;
static unsigned long ledStateStartMs = 0;

// --- NFC ---
Adafruit_PN532 nfc(NFC_SDA, NFC_SCL); // I2C constructor uses default Wire

// --- Config ---
Preferences prefs;
String deviceId;
String serverBaseUrl;

// --- Button ---
static bool lastButtonState = HIGH;
static unsigned long lastDebounceMs = 0;

// --- WiFi ---
static unsigned long lastWifiCheckMs = 0;

// WiFiManager custom parameters
static WiFiManagerParameter paramDeviceId("device_id", "Device ID (e.g. living-room)", "", 64);
static WiFiManagerParameter paramServerUrl("server_url", "Server URL (optional, e.g. http://192.168.1.100:8584)", "", 128);

// ---------------------------------------------------------------------------
// LED helpers
// ---------------------------------------------------------------------------

void setLed(LedState state) {
    ledState = state;
    ledStateStartMs = millis();
}

void updateLed() {
    unsigned long elapsed = millis() - ledStateStartMs;

    switch (ledState) {
        case LedState::IDLE:
            leds[0] = CRGB(0, 0, 40);
            break;

        case LedState::NO_WIFI: {
            // Breathing purple
            uint8_t brightness = (uint8_t)((sin(millis() / 500.0) + 1.0) * 0.5 * 60);
            leds[0] = CRGB(brightness, 0, brightness);
            break;
        }

        case LedState::WORKING:
            leds[0] = CRGB(40, 30, 0);
            break;

        case LedState::SUCCESS:
            leds[0] = CRGB(0, 40, 0);
            if (elapsed > STATUS_DISPLAY_MS) setLed(LedState::IDLE);
            break;

        case LedState::ERROR:
            leds[0] = CRGB(40, 0, 0);
            if (elapsed > STATUS_DISPLAY_MS) setLed(LedState::IDLE);
            break;
    }

    FastLED.show();
}

// ---------------------------------------------------------------------------
// NFC: read NDEF URI from tag
// ---------------------------------------------------------------------------

// NDEF URI prefix lookup table (NFC Forum URI Record Type Definition)
static const char* ndefUriPrefixes[] = {
    "",                           // 0x00
    "http://www.",                // 0x01
    "https://www.",               // 0x02
    "http://",                    // 0x03
    "https://",                   // 0x04
    "tel:",                       // 0x05
    "mailto:",                    // 0x06
    "ftp://anonymous:anonymous@", // 0x07
    "ftp://ftp.",                 // 0x08
    "ftps://",                    // 0x09
    "sftp://",                    // 0x0A
    "smb://",                     // 0x0B
    "nfs://",                     // 0x0C
    "ftp://",                     // 0x0D
    "dav://",                     // 0x0E
    "news:",                      // 0x0F
    "telnet://",                  // 0x10
    "imap:",                      // 0x11
    "rtsp://",                    // 0x12
    "urn:",                       // 0x13
    "pop:",                       // 0x14
    "sip:",                       // 0x15
    "sips:",                      // 0x16
    "tftp:",                      // 0x17
    "btspp://",                   // 0x18
    "btl2cap://",                 // 0x19
    "btgoep://",                  // 0x1A
    "tcpobex://",                 // 0x1B
    "irdaobex://",                // 0x1C
    "file://",                    // 0x1D
    "urn:epc:id:",                // 0x1E
    "urn:epc:tag:",               // 0x1F
    "urn:epc:pat:",               // 0x20
    "urn:epc:raw:",               // 0x21
    "urn:epc:",                   // 0x22
    "urn:nfc:",                   // 0x23
};

String readTagUrl() {
    uint8_t uid[7];
    uint8_t uidLength;

    if (!nfc.readPassiveTargetID(PN532_MIFARE_ISO14443A, uid, &uidLength, NFC_READ_TIMEOUT_MS)) {
        return "";
    }

    Serial.printf("Tag detected, UID length: %d\n", uidLength);

    // Read page 4 onward for NTAG2xx (NDEF data starts at page 4)
    // We'll read enough pages to get a typical URL
    uint8_t data[128];
    uint8_t totalRead = 0;

    for (uint8_t page = 4; page < 36 && totalRead < sizeof(data) - 4; page++) {
        uint8_t pageBuf[4];
        if (!nfc.ntag2xx_ReadPage(page, pageBuf)) {
            break;
        }
        memcpy(data + totalRead, pageBuf, 4);
        totalRead += 4;
    }

    if (totalRead < 4) {
        Serial.println("Failed to read NDEF data");
        return "";
    }

    // Parse TLV: search for NDEF Message TLV (type 0x03)
    uint8_t* p = data;
    uint8_t* end = data + totalRead;
    while (p < end) {
        uint8_t tlvType = *p++;
        if (tlvType == 0x00) continue; // NULL TLV
        if (tlvType == 0xFE) break;    // Terminator TLV
        if (p >= end) break;
        uint8_t tlvLen = *p++;
        if (tlvType == 0x03) {
            // Found NDEF Message TLV
            // Parse NDEF record header
            if (p + 3 > end) break;
            uint8_t header = *p;
            bool mb = header & 0x80;
            bool me = header & 0x40;
            bool sr = header & 0x10;
            uint8_t tnf = header & 0x07;
            (void)mb; (void)me;

            uint8_t typeLen = *(p + 1);
            uint8_t payloadLen;
            uint8_t* typeField;
            uint8_t* payload;

            if (sr) {
                payloadLen = *(p + 2);
                typeField = p + 3;
                payload = typeField + typeLen;
            } else {
                // Long record: 4-byte payload length
                uint32_t pl = ((uint32_t)*(p + 2) << 24) |
                              ((uint32_t)*(p + 3) << 16) |
                              ((uint32_t)*(p + 4) << 8) |
                              (uint32_t)*(p + 5);
                payloadLen = (pl > 255) ? 255 : (uint8_t)pl; // cap for our buffer
                typeField = p + 6;
                payload = typeField + typeLen;
            }

            // Check for URI record: TNF=0x01 (Well-Known), Type="U"
            if (tnf == 0x01 && typeLen == 1 && *typeField == 'U' && payloadLen > 0) {
                uint8_t prefixCode = payload[0];
                const char* prefix = "";
                if (prefixCode < sizeof(ndefUriPrefixes) / sizeof(ndefUriPrefixes[0])) {
                    prefix = ndefUriPrefixes[prefixCode];
                }
                String url = String(prefix);
                for (uint8_t i = 1; i < payloadLen && (payload + i) < end; i++) {
                    url += (char)payload[i];
                }
                Serial.printf("NDEF URL: %s\n", url.c_str());
                return url;
            }
            break;
        }
        p += tlvLen; // skip unknown TLV
    }

    Serial.println("No NDEF URI record found on tag");
    return "";
}

// ---------------------------------------------------------------------------
// URL building
// ---------------------------------------------------------------------------

String buildPlayUrl(const String& tagUrl) {
    String url;

    if (serverBaseUrl.length() > 0) {
        // Extract path from tag URL (find the third '/' which starts the path)
        int pathStart = -1;
        int slashCount = 0;
        for (int i = 0; i < (int)tagUrl.length(); i++) {
            if (tagUrl[i] == '/') {
                slashCount++;
                if (slashCount == 3) {
                    pathStart = i;
                    break;
                }
            }
        }
        String path = (pathStart >= 0) ? tagUrl.substring(pathStart) : tagUrl;

        url = serverBaseUrl;
        // Ensure no double slash
        if (url.endsWith("/") && path.startsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        url += path;
    } else {
        url = tagUrl;
    }

    // Append deviceId
    if (deviceId.length() > 0) {
        url += (url.indexOf('?') >= 0) ? "&" : "?";
        url += "deviceId=";
        url += deviceId;
    }

    return url;
}

// ---------------------------------------------------------------------------
// HTTP POST
// ---------------------------------------------------------------------------

bool postPlay(const String& url) {
    HTTPClient http;
    http.setTimeout(HTTP_TIMEOUT_MS);
    http.begin(url);
    http.addHeader("Content-Length", "0");

    Serial.printf("POST %s\n", url.c_str());
    int code = http.POST("");
    String body = http.getString();
    http.end();

    Serial.printf("Response: %d %s\n", code, body.c_str());
    return (code >= 200 && code < 300);
}

// ---------------------------------------------------------------------------
// Button
// ---------------------------------------------------------------------------

bool buttonPressed() {
    bool reading = digitalRead(BUTTON_PIN);
    if (reading != lastButtonState) {
        lastDebounceMs = millis();
    }
    lastButtonState = reading;

    // Trigger on falling edge (pressed = LOW with pullup)
    static bool prevStable = HIGH;
    bool stable = prevStable;
    if ((millis() - lastDebounceMs) > DEBOUNCE_MS) {
        stable = reading;
    }
    bool pressed = (prevStable == HIGH && stable == LOW);
    prevStable = stable;
    return pressed;
}

// ---------------------------------------------------------------------------
// WiFiManager save callback
// ---------------------------------------------------------------------------

void saveConfigCallback() {
    deviceId = String(paramDeviceId.getValue());
    serverBaseUrl = String(paramServerUrl.getValue());

    // Trim trailing slash from server URL
    while (serverBaseUrl.endsWith("/")) {
        serverBaseUrl = serverBaseUrl.substring(0, serverBaseUrl.length() - 1);
    }

    prefs.begin(NVS_NAMESPACE, false);
    prefs.putString(NVS_KEY_DEVICE_ID, deviceId);
    prefs.putString(NVS_KEY_SERVER_URL, serverBaseUrl);
    prefs.end();

    Serial.printf("Config saved - deviceId: %s, serverUrl: %s\n",
                  deviceId.c_str(), serverBaseUrl.c_str());
}

// ---------------------------------------------------------------------------
// Setup & Loop
// ---------------------------------------------------------------------------

void setup() {
    Serial.begin(115200);
    Serial.println("\n=== Blockbuster NFC Reader ===");

    // Button
    pinMode(BUTTON_PIN, INPUT_PULLUP);

    // LED
    FastLED.addLeds<WS2812B, LED_PIN, GRB>(leds, 1);
    FastLED.setBrightness(LED_BRIGHTNESS);
    setLed(LedState::NO_WIFI);
    updateLed();

    // Load saved config
    prefs.begin(NVS_NAMESPACE, true);
    deviceId = prefs.getString(NVS_KEY_DEVICE_ID, "");
    serverBaseUrl = prefs.getString(NVS_KEY_SERVER_URL, "");
    prefs.end();

    // Populate WiFiManager fields with saved values
    paramDeviceId.setValue(deviceId.c_str(), 64);
    paramServerUrl.setValue(serverBaseUrl.c_str(), 128);

    // WiFi
    WiFiManager wm;
    wm.addParameter(&paramDeviceId);
    wm.addParameter(&paramServerUrl);
    wm.setSaveParamsCallback(saveConfigCallback);
    wm.setConfigPortalTimeout(0); // No timeout - wait forever in AP mode

    if (!wm.autoConnect(AP_NAME)) {
        Serial.println("WiFi connection failed, restarting...");
        delay(1000);
        ESP.restart();
    }

    Serial.printf("WiFi connected: %s\n", WiFi.localIP().toString().c_str());
    Serial.printf("Device ID: %s\n", deviceId.c_str());
    Serial.printf("Server URL: %s\n", serverBaseUrl.c_str());

    // NFC
    Wire.begin(NFC_SDA, NFC_SCL);
    nfc.begin();
    uint32_t versiondata = nfc.getFirmwareVersion();
    if (!versiondata) {
        Serial.println("ERROR: PN532 not found! Check wiring.");
        // Blink red but continue - user can fix wiring and restart
        for (int i = 0; i < 10; i++) {
            leds[0] = (i % 2) ? CRGB::Red : CRGB::Black;
            FastLED.show();
            delay(200);
        }
    } else {
        Serial.printf("PN532 firmware: %d.%d\n",
                      (int)((versiondata >> 24) & 0xFF),
                      (int)((versiondata >> 16) & 0xFF));
        nfc.SAMConfig();
    }

    setLed(LedState::IDLE);
    Serial.println("Ready. Press button with NFC tag to play.");
}

void loop() {
    updateLed();

    // Check WiFi
    if (WiFi.status() != WL_CONNECTED) {
        if (ledState == LedState::IDLE) {
            setLed(LedState::NO_WIFI);
        }
        if (millis() - lastWifiCheckMs > WIFI_RECONNECT_INTERVAL_MS) {
            lastWifiCheckMs = millis();
            Serial.println("WiFi disconnected, attempting reconnect...");
            WiFi.reconnect();
        }
        return;
    }
    if (ledState == LedState::NO_WIFI) {
        setLed(LedState::IDLE);
    }

    // Button handling
    if (!buttonPressed()) return;
    if (ledState == LedState::WORKING) return; // Already processing

    Serial.println("Button pressed, reading NFC tag...");
    String tagUrl = readTagUrl();

    if (tagUrl.length() == 0) {
        Serial.println("No tag or no URL found");
        setLed(LedState::ERROR);
        return;
    }

    setLed(LedState::WORKING);
    updateLed();

    String playUrl = buildPlayUrl(tagUrl);
    bool ok = postPlay(playUrl);
    setLed(ok ? LedState::SUCCESS : LedState::ERROR);
}
