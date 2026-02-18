/*
  Sentri firmware for ESP32:
  - Dual MFRC522 readers (entry/exit)
  - Firestore-backed access control
  - Provisioning portal + OTA updates
*/

// Forward declaration kept above includes to satisfy Arduino IDE auto-prototype generation.
struct RfidReaderState;

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <WebServer.h>          // provisioning portal
#include <SPIFFS.h>
#include <SPI.h>
#include <MFRC522.h>
#include <time.h>
#include <vector>
#include <unordered_set>
#include <string>
#include <algorithm>
#include <ArduinoJson.h>
#include <stdarg.h>

#include <sys/time.h> // for settimeofday; already available on ESP32

// ---------------- OTA Update Support ----------------
#include <Update.h>
#include <HTTPClient.h>
#include <esp_ota_ops.h>
// ----------------------------------------------------

// -------- FirebaseClient toggles --------
// Keep Firebase include in the top include block so Arduino's auto-generated
// prototypes can resolve Firebase types (Document, Values, AsyncResult, etc.).
#define ENABLE_FIRESTORE
#define ENABLE_DATABASE
#define ENABLE_USER_AUTH
#define ENABLE_FS
#define FIREBASE_PRINTF_PORT Serial
#include <FirebaseClient.h>

// ---------------- Runtime log buffer (/logs endpoint) ----------------
static const size_t LOG_RING_MAX_LINES = 300;
static const size_t LOG_RING_MAX_CHARS_PER_LINE = 240;
static const size_t LOG_DEFAULT_TAIL_LINES = 200;

static String g_logRing[LOG_RING_MAX_LINES];
static size_t g_logRingWrite = 0;
static size_t g_logRingCount = 0;
static String g_logPartialLine;
SemaphoreHandle_t g_logMutex = NULL;

static String sanitizeLogLine(const String &line) {
  String out = line;
  out.replace("\r", "");
  if (out.length() > LOG_RING_MAX_CHARS_PER_LINE) {
    out = out.substring(0, LOG_RING_MAX_CHARS_PER_LINE);
    out += "...";
  }
  return out;
}

static void logStoreLineUnlocked(const String &line) {
  g_logRing[g_logRingWrite] = sanitizeLogLine(line);
  g_logRingWrite = (g_logRingWrite + 1) % LOG_RING_MAX_LINES;
  if (g_logRingCount < LOG_RING_MAX_LINES) g_logRingCount++;
}

static void logCaptureChunkUnlocked(const char *data, size_t len) {
  if (!data || len == 0) return;

  for (size_t i = 0; i < len; ++i) {
    char c = data[i];
    if (c == '\r') continue;
    if (c == '\n') {
      logStoreLineUnlocked(g_logPartialLine);
      g_logPartialLine = "";
    } else {
      g_logPartialLine += c;
      if (g_logPartialLine.length() >= LOG_RING_MAX_CHARS_PER_LINE) {
        logStoreLineUnlocked(g_logPartialLine);
        g_logPartialLine = "";
      }
    }
  }
}

static void logCaptureChunk(const char *data, size_t len) {
  if (g_logMutex && xSemaphoreTake(g_logMutex, portMAX_DELAY) == pdTRUE) {
    logCaptureChunkUnlocked(data, len);
    xSemaphoreGive(g_logMutex);
  } else {
    logCaptureChunkUnlocked(data, len);
  }
}

String getLogDump(size_t tailLines = LOG_DEFAULT_TAIL_LINES) {
  if (tailLines == 0) tailLines = LOG_DEFAULT_TAIL_LINES;
  if (tailLines > LOG_RING_MAX_LINES) tailLines = LOG_RING_MAX_LINES;

  String out;
  if (g_logMutex && xSemaphoreTake(g_logMutex, portMAX_DELAY) == pdTRUE) {
    size_t lines = (g_logRingCount < tailLines) ? g_logRingCount : tailLines;
    size_t start = (g_logRingWrite + LOG_RING_MAX_LINES - lines) % LOG_RING_MAX_LINES;

    out.reserve(lines * 48 + 64);
    out += "# Sentri logs\n";
    out += "# lines=" + String((unsigned)lines) + "\n";
    for (size_t i = 0; i < lines; ++i) {
      size_t idx = (start + i) % LOG_RING_MAX_LINES;
      out += g_logRing[idx];
      out += "\n";
    }
    if (g_logPartialLine.length()) {
      out += g_logPartialLine + " [partial]\n";
    }

    xSemaphoreGive(g_logMutex);
  } else {
    out = "# Logs unavailable (mutex not ready)\n";
  }
  return out;
}

void clearLogDump() {
  if (g_logMutex && xSemaphoreTake(g_logMutex, portMAX_DELAY) == pdTRUE) {
    for (size_t i = 0; i < LOG_RING_MAX_LINES; ++i) g_logRing[i] = "";
    g_logRingWrite = 0;
    g_logRingCount = 0;
    g_logPartialLine = "";
    xSemaphoreGive(g_logMutex);
  } else {
    for (size_t i = 0; i < LOG_RING_MAX_LINES; ++i) g_logRing[i] = "";
    g_logRingWrite = 0;
    g_logRingCount = 0;
    g_logPartialLine = "";
  }
}

class MirroredSerial : public Print {
public:
  explicit MirroredSerial(HardwareSerial *port) : _port(port) {}

  void begin(unsigned long baud) { _port->begin(baud); }
  void begin(unsigned long baud, uint32_t config) { _port->begin(baud, config); }
  void flush() { _port->flush(); }
  int available() { return _port->available(); }
  int read() { return _port->read(); }
  int peek() { return _port->peek(); }

  size_t printf(const char *format, ...) {
    if (!format) return 0;
    va_list args;
    va_start(args, format);
    int needed = vsnprintf(nullptr, 0, format, args);
    va_end(args);
    if (needed <= 0) return 0;

    std::vector<char> buf((size_t)needed + 1);
    va_start(args, format);
    vsnprintf(buf.data(), buf.size(), format, args);
    va_end(args);

    logCaptureChunk(buf.data(), (size_t)needed);
    return _port->write((const uint8_t *)buf.data(), (size_t)needed);
  }

  size_t write(uint8_t c) override {
    char ch = (char)c;
    logCaptureChunk(&ch, 1);
    return _port->write(c);
  }

  size_t write(const uint8_t *buffer, size_t size) override {
    if (buffer && size) logCaptureChunk((const char *)buffer, size);
    return _port->write(buffer, size);
  }

  using Print::write;

private:
  HardwareSerial *_port;
};

static MirroredSerial gMirroredSerial(&Serial);

// Keep Arduino/Firebase headers on the original Serial object, then mirror
// all sketch-level Serial writes into the in-memory /logs buffer.
#define Serial gMirroredSerial

// ---------------- Firmware Version ----------------
#define FIRMWARE_VERSION "1.0"
#define FIRMWARE_BUILD_DATE __DATE__ " " __TIME__
// --------------------------------------------------

// ---------------- Prefill for provisioning page ONLY (not auto-saved)
#define API_KEY_PREFILL   "AIzaSyA798vpOSokpRWCcHS08FbgRP7ldczqJ4w"
#define PROJECT_ID_PREFILL "sentri-ban2r1"

// ---------------- Hardware: Dual MFRC522 + Relay ---------
#define MFRC522_ENTRY_SS   5
#define MFRC522_ENTRY_RST  2
#define MFRC522_EXIT_SS    22
#define MFRC522_EXIT_RST   21

#define FACTORY_RESET_PIN  30       // active-high reset input
#define RELAY              32       // relay control pin
#define DOOR_PULSE_MS      3000     // unlock pulse ms

// Memory and stability thresholds
#define MIN_FREE_HEAP_BYTES    20000    // minimum free heap before taking action
#define HEAP_WARNING_THRESHOLD 30000    // warn when heap drops below this
#define MAX_LOG_QUEUE_LINES    500      // maximum queued logs before forced flush
#define WATCHDOG_TIMEOUT_SEC   30       // watchdog timer timeout in seconds

// Timing constants for non-blocking operations
#define WIFI_RECONNECT_LOG_INTERVAL_MS  60000UL  // log WiFi status every 60s
#define WIFI_RECONNECT_ATTEMPT_MS       10000UL  // force reconnect attempt every 10s when disconnected
#define FIREBASE_BACKOFF_MAX_MS         60000UL  // max backoff delay
#define FIREBASE_BACKOFF_BASE_MS        1000UL   // base backoff delay
#define FIREBASE_BACKOFF_MAX_EXP        12       // max exponent for backoff

// OTA Update constants
#define OTA_CHECK_INTERVAL_MS           3600000UL  // check for updates every hour
#define OTA_TIMEOUT_MS                  300000UL   // 5 minute timeout for OTA download
#define OTA_BUFFER_SIZE                 1024       // buffer size for OTA chunks

// Set to 1 to enable high-volume diagnostics for troubleshooting.
#ifndef VERBOSE_LOGS
  #define VERBOSE_LOGS 0
#endif

#define LOG_ERROR(fmt, ...) Serial.printf("[ERROR] " fmt "\n", ##__VA_ARGS__)
#define LOG_WARN(fmt, ...)  Serial.printf("[WARN] " fmt "\n", ##__VA_ARGS__)
#define LOG_INFO(fmt, ...)  Serial.printf("[INFO] " fmt "\n", ##__VA_ARGS__)
#if VERBOSE_LOGS
  #define LOG_DEBUG(fmt, ...) Serial.printf("[DEBUG] " fmt "\n", ##__VA_ARGS__)
#else
  #define LOG_DEBUG(fmt, ...) ((void)0)
#endif

// Technician-only full-wipe pin: must be jumper-to-GND while holding FACTORY_RESET_PIN at boot.
// Set to -1 to disable full-wipe capability.
#ifndef FACTORY_RESET_FULL_PIN
  #define FACTORY_RESET_FULL_PIN 25
#endif

// Max bytes expected from UID reads.
#ifndef UID_MAX_BYTES
  #define UID_MAX_BYTES 16
#endif

const int RELAY_LOCKED_STATE   = LOW;
const int RELAY_UNLOCKED_STATE = HIGH;

MFRC522 rfidEntry(MFRC522_ENTRY_SS, MFRC522_ENTRY_RST);
MFRC522 rfidExit (MFRC522_EXIT_SS,  MFRC522_EXIT_RST);

// ---------------- Firebase client objects
WiFiClientSecure ssl_client;
WiFiClientSecure stream_ssl_client;
using AsyncClient = AsyncClientClass;
AsyncClient aClient(ssl_client);
AsyncClient streamClient(stream_ssl_client);

FirebaseApp app;
UserAuth user_auth("", "", "");       // real values loaded after provisioning
Firestore::Documents Docs;
RealtimeDatabase Database;

// ---------------- Files & thresholds
static const char* FILE_CONFIG       = "/config.json";
static const char* FILE_CARD_CACHE   = "/cards_cache.ndjson";
static const char* FILE_LOG_QUEUE    = "/access_logs.ndjson";
static const char* FILE_LOG_COUNTER  = "/log_counter.txt";
static const char* FILE_ACTIVITY     = "/activity_state.txt";
static const char* FILE_ACCESS_LIST  = "/access_list.json";
static const char* FILE_ACCESS_META  = "/access_meta.json"; // new meta file with updated_at
static const char* FILE_FIRMWARE_META = "/firmware_meta.json"; // OTA firmware version tracking

// Wi-Fi recovery timing (non-blocking)
// retry Wi-Fi connections for 2 minutes before opening AP
static const unsigned long WIFI_LOOKUP_TIMEOUT_MS = 2UL * 60UL * 1000UL; // 2 minutes

// keep AP/provisioning portal open for 5 minutes
static const unsigned long PROVISION_WINDOW_MS    = 5UL * 60UL * 1000UL; // 5 minutes

static const uint32_t CARD_CACHE_TTL_SEC = 24U * 3600U;  // 24h card cache
static const uint32_t HEARTBEAT_MS       = 60UL * 60UL * 1000UL; // hourly = 60 * 60 * 1000
static const uint32_t FLUSH_INTERVAL_MS  = 1UL * 60UL * 1000UL; // every minute = 60 * 1000
static const float    FLUSH_FS_THRESH    = 0.80f;                 // 80% SPIFFS

// runtime wifi recovery state
unsigned long g_wifiFailStart = 0;       // when the current "search for wifi" window started
unsigned long g_provWindowStart = 0;     // when the provisioning window started
bool g_inProvWindow = false;             // are we currently serving the provisioning portal?
unsigned long g_lastWifiReconnectAttempt = 0; // last explicit reconnect attempt

// Firebase failure handling
int firebaseFailCount = 0;
const int FIREBASE_FAIL_THRESHOLD = 5;          // after this many failures -> open AP
const unsigned long FIREBASE_FAIL_RESET_MS = 5 * 60UL * 1000UL; // reset fail counter after 5 minutes
unsigned long firebaseLastFailAt = 0;
bool g_firestoreBusy = false;
bool g_activeCardsStreamStarted = false;
bool g_activeCardsStreamHealthy = false;
unsigned long g_lastActiveCardsStreamEventMs = 0;
unsigned long g_activeCardsStreamStartedAtMs = 0;
unsigned long g_nextActiveCardsStreamRestartAt = 0;
String g_lastActiveCardsStreamEvent = "";
bool g_activeCardsRefreshPending = false;
unsigned long g_activeCardsRefreshAt = 0;
String g_lastActiveCardsVersionToken = "";
const unsigned long ACTIVE_CARDS_STREAM_DEBOUNCE_MS = 250UL;
const unsigned long ACTIVE_CARDS_STREAM_RETRY_MS = 5000UL;
const unsigned long ACTIVE_CARDS_STREAM_FIRST_EVENT_TIMEOUT_MS = 30000UL;
const unsigned long ACTIVE_CARDS_STREAM_STALE_MS = 180000UL;
const unsigned long ACTIVE_CARDS_STREAM_RESTART_BACKOFF_MS = 15000UL;
static const char* ACTIVE_CARDS_VERSION_ROOT = "/sentri/active_cards_version";

// Non-blocking Firebase init helpers
void firebaseInitStart();
void firebaseInitPoll();

// Request flag to start Firebase init from main loop (set from Wi-Fi event)
bool g_requestFirebaseInit = false;

// ---------------- Runtime state (populated from /config.json after provisioning)
String g_ssid, g_pass, g_apiKey, g_projectId, g_userEmail, g_userPass, g_businessId, g_deviceName, g_databaseUrl;
String g_mac;
bool   g_activityEnabled = true;  // true=normal (locked & pulse on grant), false=held unlocked
unsigned long g_lastHeartbeat = 0;
unsigned long g_lastFlush     = 0;

// ---- Time sync / delayed registration guards ----
bool g_deviceRegisteredAfterTimeSync = false;

// Runtime reset state (non-blocking)
// Note: pressStart and pressed state are local static in runtimeResetTick()
const unsigned long RESET_CONFIRM_MS = 3000UL; // require 3s hold to confirm reset

const unsigned long RESET_PORTAL_OPEN_MS = 200UL; // >=200ms and <1s on release => open provisioning portal
const unsigned long RESET_SHORT_MS = 1000UL;  // >=1s on release => normal reset (clear credentials)
const unsigned long RESET_LONG_MS  = 5000UL;  // >=5s on release => hard reset (format SPIFFS)

// Non-blocking firebase init state
bool g_firebaseInitInProgress = false;
unsigned long g_firebaseInitStartedAt = 0;
const unsigned long FIREBASE_INIT_POLL_TIMEOUT_MS = 15000UL; // try ~15s before giving up this attempt
const unsigned long FIREBASE_INIT_POLL_SLICE_MS = 200;      // poll slice interval
unsigned long g_lastFirebasePoll = 0;

// GOT_IP / firebase init scheduling
unsigned long g_gotIpAt = 0;                   // millis() when GOT_IP seen
unsigned long g_nextFirebaseInitAttempt = 0;   // earliest millis() to next attempt (backoff)
const unsigned long GOT_IP_INIT_DELAY_MS = 700; // wait 700ms after GOT_IP before calling initializeApp()
const unsigned long FIREBASE_INIT_RETRY_MS = 5000; // retry delay after a failed init attempt

// ---------------- Provisioning portal
WebServer portal(80);
const char* AP_PASS = "12345678";
bool g_portalActive = false;
bool g_portalHandlersRegistered = false;
bool g_portalServerStarted = false;

// Force-provisioning window (used when Firebase auth keeps failing)
unsigned long g_forcedProvUntil = 0; // millis() until which portal must remain open
const unsigned long FIREBASE_PROVISION_WINDOW_MS = 300000UL; // 5 minutes

// ---------------- OTA Update State ----------------
String g_otaUpdateUrl = "";              // URL to fetch firmware from
bool g_otaInProgress = false;            // OTA update currently running
unsigned long g_lastOtaCheck = 0;        // last time we checked for updates
bool g_otaUpdateAvailable = false;       // update available flag
String g_availableFirmwareVersion = ""; // version available for update
// --------------------------------------------------

// --------- Utility declarations (order matters for some callbacks)
struct RfidReaderState;
void stopProvisionPortal();
void startProvisionPortal(unsigned long forcedWindowMs = 0);
void ensurePortalServerRunning();
void WiFiEventCB(WiFiEvent_t event);
bool wifiConnect(uint32_t timeoutMs = 15000);
void wifiRecoveryTick();
void kickWifiReconnect(bool forceBegin = false);
void fetchActiveCardsFromFirebase_v2();
void startActiveCardsVersionStream();
void activeCardsSyncTick();
void activeCardsStreamWatchdogTick();
void processActiveCardsVersionStream(AsyncResult &r);
String escapeJson(const String &s);
void timeSync();
void logConfigSummary();
bool isUidActive(const String &uidNorm, size_t *setSizeOut = nullptr);
bool clearCredentialsForNormalReset();
bool formatSpiffsForHardReset();
void handleLogs();
void processRfidScan(MFRC522 &reader,
                     RfidReaderState &state,
                     const char *logType,
                     const char *logLabel,
                     bool enforceEntryRules);

// ---------------- Active cards structures and O(1) lookup
struct ActiveCardEntry {
  String card_data;
  String start_time; // "HH:MM"
  String end_time;   // "HH:MM"
};
std::vector<ActiveCardEntry> g_activeCards;
const size_t MAX_ACTIVE_CARDS = 1024;

// O(1) lookup set using UPPERCASE ASCII strings for fast membership tests
static std::unordered_set<std::string> g_activeSet;

// Protect all active card data access (reads and writes).
SemaphoreHandle_t g_activeCardsMutex = NULL;
bool g_updatingActiveCards = false;

// File system mutex to prevent concurrent SPIFFS operations
SemaphoreHandle_t g_fileMutex = NULL;
// ---------------------------------------------------------------------------------

// Relay state machine to avoid blocking delay() calls.
enum RelayState { RELAY_IDLE, RELAY_PULSING };
RelayState g_relayState = RELAY_IDLE;
unsigned long g_relayPulseStartMs = 0;

// Per-reader debounce and presence tracking.
struct RfidReaderState {
  String lastUid;              // last scanned UID
  unsigned long lastScanMs;    // when last scan occurred
  bool cardPresent;            // is card currently present
};
RfidReaderState g_entryReaderState = {"", 0, false};
RfidReaderState g_exitReaderState = {"", 0, false};
const unsigned long RFID_DEBOUNCE_MS = 2000;  // minimum time between same card scans

enum TimeSyncState { TIME_SYNC_IDLE, TIME_SYNC_SNTP_WAIT, TIME_SYNC_HTTP_FALLBACK };
TimeSyncState g_timeSyncState = TIME_SYNC_IDLE;
unsigned long g_timeSyncStartMs = 0;
int g_timeSyncAttempts = 0;
const unsigned long TIME_SYNC_SNTP_TIMEOUT_MS = 30000;  // 30s for SNTP
const unsigned long TIME_SYNC_HTTP_TIMEOUT_MS = 10000;  // 10s for HTTP fallback
const int TIME_SYNC_MAX_ATTEMPTS = 3;
bool g_timeIsSynced = false;

#include <esp_task_wdt.h>

// Forward declaration for functions used before definition
void flushQueueBatch(size_t maxLines = 50);

// Check heap and take action if critically low
void checkHeapHealth() {
  uint32_t freeHeap = ESP.getFreeHeap();
  
  if (freeHeap < MIN_FREE_HEAP_BYTES) {
    LOG_ERROR("Heap exhausted (free=%u). Forcing emergency flush.", freeHeap);
    // Emergency: try to free memory
    flushQueueBatch(200); // flush more aggressively
    
    // If still critical after flush, restart to prevent crash
    if (ESP.getFreeHeap() < MIN_FREE_HEAP_BYTES) {
      LOG_ERROR("Heap still low after flush. Restarting device.");
      delay(100);
      ESP.restart();
    }
  } else if (freeHeap < HEAP_WARNING_THRESHOLD) {
    LOG_WARN("Low heap detected (free=%u).", freeHeap);
  }
}

// Reset watchdog timer safely (check if task is subscribed FIRST)
inline void feedWatchdog() {
  if (esp_task_wdt_status(NULL) == ESP_OK) {
    esp_task_wdt_reset();
  }
}

// Count lines in the local queue file to bound memory/storage usage.
size_t countLogQueueLines() {
  if (xSemaphoreTake(g_fileMutex, portMAX_DELAY) != pdTRUE) return 0;
  File f = SPIFFS.open(FILE_LOG_QUEUE, "r");
  if (!f) {
    xSemaphoreGive(g_fileMutex);
    return 0;
  }
  size_t count = 0;
  while (f.available()) {
    f.readStringUntil('\n');
    count++;
  }
  f.close();
  xSemaphoreGive(g_fileMutex);
  return count;
}
// -------------------------------------------------------------------------

// ---------------- Utilities (SPIFFS, time, Wi-Fi)
bool fsEnsure() {
  Serial.println("Mounting SPIFFS...");
  // Only normal mount is allowed here. Formatting is restricted to hard reset flow.
  if (SPIFFS.begin()) {
    Serial.println("SPIFFS mounted (normal).");
    Serial.printf("SPIFFS: used=%u total=%u\n", (unsigned)SPIFFS.usedBytes(), (unsigned)SPIFFS.totalBytes());
    return true;
  }

  LOG_ERROR("SPIFFS mount failed. Auto-format is disabled.");
  LOG_WARN("Use hard reset (long press) to format SPIFFS.");
  return false;
}

// Normalize UID/card_data to canonical uppercase hex without separators.
String normalizeUidStr(const String &s) {
  String out;
  out.reserve(s.length()); // pre-allocate expected size
  for (size_t i = 0; i < s.length(); ++i) {
    char c = s[i];
    // accept hex digits only; ignore whitespace/other separators
    if ((c >= '0' && c <= '9') ||
        (c >= 'A' && c <= 'F') ||
        (c >= 'a' && c <= 'f')) {
      if (c >= 'a' && c <= 'f') c = c - ('a' - 'A');
      out += c;
    }
  }
  return out;
}

bool fileExists(const char* p) { return SPIFFS.exists(p); }
size_t fsUsed()  { return SPIFFS.usedBytes(); }
size_t fsTotal() { return SPIFFS.totalBytes(); }
float  fsUsage() { return fsTotal() ? (float)fsUsed() / (float)fsTotal() : 0.0f; }

String readFile(const char* path) {
  File f = SPIFFS.open(path, "r");
  if (!f) return "";
  String s = f.readString();
  f.close();
  return s;
}
bool writeFile(const char* path, const String& data) {
  File f = SPIFFS.open(path, "w");
  if (!f) return false;
  f.print(data);
  f.close();
  return true;
}
// Append a line to a file with SPIFFS mutex protection.
bool appendLine(const char* path, const String& line) {
  if (xSemaphoreTake(g_fileMutex, portMAX_DELAY) != pdTRUE) return false;
  File f = SPIFFS.open(path, "a");
  if (!f) {
    xSemaphoreGive(g_fileMutex);
    return false;
  }
  f.println(line);
  f.close();
  xSemaphoreGive(g_fileMutex);
  return true;
}

String trimStr(const String &s) {
  String out = s;
  // remove leading whitespace
  while (out.length() && (out[0] == ' ' || out[0] == '\r' || out[0] == '\n' || out[0] == '\t')) out = out.substring(1);
  // remove trailing whitespace
  while (out.length() && (out[out.length()-1] == ' ' || out[out.length()-1] == '\r' || out[out.length()-1] == '\n' || out[out.length()-1] == '\t')) out = out.substring(0, out.length()-1);
  return out;
}

// Build RFC3339 UTC timestamp if wall clock is synchronized.
String rfc3339NowUTC() {
  if (!g_timeIsSynced) {
    // IMPORTANT: Never return epoch for Firestore
    // Return empty string to signal "do not write timestamp"
    return "";
  }

  time_t now = time(nullptr);
  struct tm* tm_utc = gmtime(&now);
  char buf[32];
  strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", tm_utc);
  return String(buf);
}

// ================= SAFE FIRESTORE TIMESTAMP HELPER =================
// FirebaseClient WILL abort() if TimestampValue() gets an empty string.
// This helper prevents hard crashes.
bool safeAddTimestamp(Document<Values::Value> &doc,
                      const String &field) {
  String ts = rfc3339NowUTC();
  if (ts.length() == 0) {
    LOG_WARN("Skipping timestamp field '%s' (time not ready).", field.c_str());
    return false;
  }
  doc.add(field, Values::Value(Values::TimestampValue(ts)));
  return true;
}
// ===================================================================

// Escape JSON-special characters for safe string interpolation.
String escapeJson(const String &s) {
  String out;
  out.reserve(s.length() + 10);
  for (size_t i = 0; i < s.length(); ++i) {
    char c = s[i];
    switch (c) {
      case '"':  out += "\\\""; break;
      case '\\': out += "\\\\"; break;
      case '\n': out += "\\n";  break;
      case '\r': out += "\\r";  break;
      case '\t': out += "\\t";  break;
      default:   out += c;      break;
    }
  }
  return out;
}

// Return current local time in HH:MM, or 00:00 when clock is not synced.
String currentHHMM() {
  time_t now = time(nullptr);
  // Validate time is set
  if (now < 1577836800) {
    return "00:00"; // fallback if time not synced
  }
  struct tm *tm_local = localtime(&now);
  char buf[6];
  snprintf(buf, sizeof(buf), "%02d:%02d", tm_local->tm_hour, tm_local->tm_min);
  return String(buf);
}

bool timeInRange(const String &startHHMM, const String &endHHMM, const String &targetHHMM) {
  auto toMin = [](const String &s)->int {
    if (s.length() < 4) return -1;
    int h = s.substring(0,2).toInt();
    int m = s.substring(3,5).toInt();
    return h*60 + m;
  };
  int s = toMin(startHHMM);
  int e = toMin(endHHMM);
  int t = toMin(targetHHMM);
  if (s<0 || e<0 || t<0) return false;
  if (s <= e) return (t >= s && t <= e);
  // overnight e.g. 22:00-06:00 -> allow if t >= s OR t <= e
  return (t >= s || t <= e);
}

// ---------------- Config load/check (NO seeding; only read if exists)
String jsonGetStr(const String& json, const String& key) {
  // Lightweight extractor: finds "key":"value"
  String kq="\""+key+"\":";
  int k=json.indexOf(kq); if(k<0) return "";
  int q1=json.indexOf('"',k+kq.length()); if(q1<0) return "";
  int q2=json.indexOf('"',q1+1);          if(q2<0) return "";
  return json.substring(q1+1,q2);
}

// Lightweight extractor for string OR numeric scalar values in a flat JSON object.
String jsonGetScalar(const String &json, const String &key) {
  String kq = "\"" + key + "\":";
  int k = json.indexOf(kq);
  if (k < 0) return "";

  int i = k + kq.length();
  while (i < (int)json.length() && (json[i] == ' ' || json[i] == '\n' || json[i] == '\r' || json[i] == '\t')) i++;
  if (i >= (int)json.length()) return "";

  if (json[i] == '"') {
    int q2 = json.indexOf('"', i + 1);
    if (q2 < 0) return "";
    return json.substring(i + 1, q2);
  }

  int end = i;
  while (end < (int)json.length() && json[end] != ',' && json[end] != '}' && json[end] != '\n' && json[end] != '\r') end++;
  String out = json.substring(i, end);
  out.trim();
  return out == "null" ? "" : out;
}
// Load config from SPIFFS with basic size and parse validation.
void loadConfigIfPresent() {
  if (!SPIFFS.exists(FILE_CONFIG)) {
    LOG_INFO("Config file not found.");
    g_ssid = g_pass = g_apiKey = g_projectId =
    g_userEmail = g_userPass = g_businessId = g_deviceName = g_databaseUrl = "";
    return;
  }

  File f = SPIFFS.open(FILE_CONFIG, "r");
  if (!f) {
    LOG_ERROR("Failed to open config file.");
    return;
  }

  size_t sz = f.size();
  if (sz == 0 || sz > 4096) {
    LOG_ERROR("Invalid config file size (%u bytes).", (unsigned)sz);
    f.close();
    return;
  }

  DynamicJsonDocument doc(4096);

  DeserializationError err = deserializeJson(doc, f);
  f.close();

  if (err) {
    LOG_ERROR("Config JSON parse error: %s", err.c_str());
    return;
  }

  g_ssid       = doc["wifi_ssid"]     | "";
  g_pass       = doc["wifi_password"] | "";
  g_apiKey     = doc["api_key"]       | "";
  g_projectId  = doc["project_id"]    | "";
  g_userEmail  = doc["user_email"]    | "";
  g_userPass   = doc["user_password"] | "";
  g_businessId = doc["business_id"]   | "";
  g_deviceName = doc["device_name"]   | "";
  g_databaseUrl = doc["database_url"] | "";

  LOG_INFO("Config loaded successfully.");
}


bool configComplete() {
  return g_ssid.length() && g_pass.length() &&
         g_apiKey.length() && g_projectId.length() &&
         g_userEmail.length() && g_userPass.length() &&
         g_businessId.length();
}

void logConfigSummary() {
  LOG_INFO(
    "Config summary: ssid=%s api_key=%s project_id=%s user_email=%s business_id=%s device_name=%s database_url=%s",
    g_ssid.length() ? "set" : "missing",
    g_apiKey.length() ? "set" : "missing",
    g_projectId.length() ? "set" : "missing",
    g_userEmail.length() ? "set" : "missing",
    g_businessId.length() ? "set" : "missing",
    g_deviceName.length() ? "set" : "missing",
    g_databaseUrl.length() ? "set" : "missing"
  );
}

bool clearCredentialsForNormalReset() {
  // Preserve non-credential fields while clearing only requested secrets.
  String cfg = "{";
  cfg += "\"wifi_ssid\":\"\",";
  cfg += "\"wifi_password\":\"\",";
  cfg += "\"api_key\":\"" + escapeJson(g_apiKey) + "\",";
  cfg += "\"project_id\":\"" + escapeJson(g_projectId) + "\",";
  cfg += "\"user_email\":\"\",";
  cfg += "\"user_password\":\"\",";
  cfg += "\"business_id\":\"" + escapeJson(g_businessId) + "\",";
  cfg += "\"device_name\":\"" + escapeJson(g_deviceName) + "\",";
  cfg += "\"database_url\":\"" + escapeJson(g_databaseUrl) + "\"";
  cfg += "}";

  bool ok = writeFile(FILE_CONFIG, cfg);
  if (ok) {
    g_ssid = "";
    g_pass = "";
    g_userEmail = "";
    g_userPass = "";
  }
  return ok;
}

bool formatSpiffsForHardReset() {
  LOG_WARN("Hard reset requested. Formatting SPIFFS...");
  bool ok = SPIFFS.format();
  if (ok) {
    LOG_INFO("SPIFFS formatted successfully.");
  } else {
    LOG_ERROR("SPIFFS format failed.");
  }
  return ok;
}

// ---------------- Provisioning portal (AP mode)
String apSSID() {
  return "Sentri-Setup";
}

// Start non-blocking SNTP sync. Completion is handled in timeSyncPoll().
void timeSyncStart() {
  if (WiFi.status() != WL_CONNECTED) {
    LOG_WARN("timeSyncStart skipped: WiFi not connected.");
    return;
  }

  // Set timezone to IST (UTC+5:30)
  setenv("TZ", "IST-5:30", 1);
  tzset();

  const char* ntpServers[] = { "time.google.com", "pool.ntp.org", "time.nist.gov" };
  LOG_INFO("Starting non-blocking time sync (SNTP).");
  
  // Use GMT offset = 0 since timezone is already set via TZ
  configTime(0, 0, ntpServers[0], ntpServers[1], ntpServers[2]);
  
  g_timeSyncState = TIME_SYNC_SNTP_WAIT;
  g_timeSyncStartMs = millis();
  g_timeSyncAttempts = 0;
}

// Poll non-blocking time sync and run post-sync registration once.
void timeSyncPoll() {
  if (g_timeSyncState == TIME_SYNC_IDLE && g_timeIsSynced) return;

  unsigned long now = millis();

  if (!g_timeIsSynced && g_timeSyncState == TIME_SYNC_SNTP_WAIT) {
    time_t t = time(nullptr);
    if (t > 1577836800) {
      LOG_INFO("Time sync complete. Local time=%s", currentHHMM().c_str());
      g_timeIsSynced = true;
      g_timeSyncState = TIME_SYNC_IDLE;
    } else if (now - g_timeSyncStartMs > TIME_SYNC_SNTP_TIMEOUT_MS) {
      LOG_WARN("Time sync timeout. Continuing without synchronized time.");
      g_timeSyncState = TIME_SYNC_IDLE;
    }
  }

  // ---- SAFE delayed registration ----
  if (g_timeIsSynced &&
      app.ready() &&
      !g_deviceRegisteredAfterTimeSync &&
      !g_firestoreBusy) {

    LOG_INFO("Time synced - performing delayed device registration.");
    fsRegisterOrUpdateDevice();
    g_deviceRegisteredAfterTimeSync = true;
  }
}

// [DEPRECATED] Old blocking time sync - kept for compatibility but should not be used
void timeSync() {
  LOG_WARN("timeSync() is deprecated, use timeSyncStart() + timeSyncPoll().");
  timeSyncStart();
  // Do a few quick checks
  for (int i = 0; i < 5; i++) {
    delay(1000);
    time_t t = time(nullptr);
    if (t > 1577836800) {
      g_timeIsSynced = true;
      LOG_INFO("Time synced (blocking compatibility mode).");
      return;
    }
  }
  LOG_WARN("Time sync incomplete (blocking mode timed out).");
}

void handleRoot() {
  // Prefill: use configured values if present; else use PREFILL for API/Project only
  String apiPrefill  = g_apiKey.length()    ? g_apiKey    : String(API_KEY_PREFILL);
  String projPrefill = g_projectId.length() ? g_projectId : String(PROJECT_ID_PREFILL);

  String page =
    "<!doctype html><html><head><meta charset='utf-8'/>"
    "<title>Sentri Setup</title>"
    "<style>body{font-family:system-ui;margin:20px;max-width:800px}"
    "input,textarea{width:100%;padding:8px;margin:6px 0}button{padding:10px 16px}</style>"
    "</head><body>"
    "<h2>Sentri Device Setup</h2>"
    "<form method='POST' action='/save'>"
    "<h3>Wi-Fi</h3>"
    "<label>SSID</label><input name='wifi_ssid' value='" + g_ssid + "'/>"
    "<label>Password</label><input name='wifi_password' type='password' value='" + g_pass + "'/>"
    "<h3>Firebase</h3>"
    "<label>API Key</label><input name='api_key' value='" + apiPrefill + "'/>"
    "<label>Project ID</label><input name='project_id' value='" + projPrefill + "'/>"
    "<label>Realtime DB URL (optional)</label><input name='database_url' value='" + g_databaseUrl + "' placeholder='https://sentri-ban2r1-default-rtdb.asia-southeast1.firebasedatabase.app'/>"
    "<label>User Email</label><input name='user_email' value='" + g_userEmail + "'/>"
    "<label>User Password</label><input name='user_password' type='password' value='" + g_userPass + "'/>"
    "<label>Business ID</label><input name='business_id' value='" + g_businessId + "'/>"
    "<label>Device Name</label><input name='device_name' value='" + g_deviceName + "' placeholder='e.g., Main Gate'/>"
    "<br/><button type='submit'>Save & Reboot</button>"
    "</form>"
    "<p style='opacity:.7;margin-top:16px'>AP SSID: " + apSSID() + " - MAC: " + g_mac + "</p>"
    "<p style='opacity:.7;margin-top:8px'><a href='/status'>/status (diagnostics)</a></p>"
    "<p style='opacity:.7;margin-top:8px'><a href='/logs'>/logs (serial logs)</a></p>"
    "</body></html>";
  portal.send(200, "text/html", page);
}
void handleSave() {
  // Save exactly what user entered (no hidden defaults)
  String cfg = "{";
  cfg += "\"wifi_ssid\":\""    + escapeJson(portal.arg("wifi_ssid"))    + "\","; 
  cfg += "\"wifi_password\":\""+ escapeJson(portal.arg("wifi_password")) + "\","; 
  cfg += "\"api_key\":\""      + escapeJson(portal.arg("api_key"))      + "\","; 
  cfg += "\"project_id\":\""   + escapeJson(portal.arg("project_id"))   + "\","; 
  cfg += "\"database_url\":\"" + escapeJson(portal.arg("database_url")) + "\","; 
  cfg += "\"user_email\":\""   + escapeJson(portal.arg("user_email"))   + "\","; 
  cfg += "\"user_password\":\""+ escapeJson(portal.arg("user_password"))+ "\","; 
  cfg += "\"business_id\":\""  + escapeJson(portal.arg("business_id"))  + "\","; 
  cfg += "\"device_name\":\""  + escapeJson(portal.arg("device_name"))  + "\"";
  cfg += "}";

  // write atomically to tmp then rename with fallback
  const char* tmp = "/config.tmp";
  if (writeFile(tmp, cfg)) {
    bool renamed = SPIFFS.rename(tmp, FILE_CONFIG);
    if (!renamed) {
      // fallback: read tmp and write to final
      String t = readFile(tmp);
      if (t.length()) {
        if (SPIFFS.exists(FILE_CONFIG)) SPIFFS.remove(FILE_CONFIG);
        writeFile(FILE_CONFIG, t);
        SPIFFS.remove(tmp);
        LOG_INFO("Config saved (fallback overwrite).");
      } else {
        LOG_ERROR("Failed to save config: temp file read returned empty.");
      }
    } else {
      LOG_INFO("Config saved.");
    }
  } else {
    LOG_ERROR("Failed to write temporary config file.");
  }

  portal.send(200, "text/html",
    "<html><body><h3>Saved. Rebooting...</h3>"
    "<p>You can close this page.</p></body></html>");
  delay(300);
  ESP.restart();
}
void handleReboot() {
  portal.send(200, "text/plain", "Rebooting");
  delay(200);
  ESP.restart();
}

// Diagnostic endpoint while provisioning portal is active.
void handleStatus() {
  // Read shared card collections under mutex for consistency.
  size_t cardsLoaded = 0;
  size_t setSize = 0;
  if (xSemaphoreTake(g_activeCardsMutex, portMAX_DELAY) == pdTRUE) {
    cardsLoaded = g_activeCards.size();
    setSize = g_activeSet.size();
    xSemaphoreGive(g_activeCardsMutex);
  }
  
  String s = "{";
  s += "\"mac\":\"" + g_mac + "\",";
  s += "\"firmware_version\":\"" + String(FIRMWARE_VERSION) + "\",";
  s += "\"build_date\":\"" + String(FIRMWARE_BUILD_DATE) + "\",";
  s += "\"wifi_status\":" + String(WiFi.status()) + ",";
  s += "\"ip\":\"" + WiFi.localIP().toString() + "\",";
  s += "\"rssi\":" + String(WiFi.RSSI()) + ",";
  s += "\"firebase_ready\":" + String(app.ready() ? "true" : "false") + ",";
  s += "\"active_cards_stream_started\":" + String(g_activeCardsStreamStarted ? "true" : "false") + ",";
  s += "\"active_cards_stream_healthy\":" + String(g_activeCardsStreamHealthy ? "true" : "false") + ",";
  s += "\"active_cards_stream_started_at_ms\":" + String(g_activeCardsStreamStartedAtMs) + ",";
  s += "\"active_cards_stream_last_event_ms\":" + String(g_lastActiveCardsStreamEventMs) + ",";
  s += "\"active_cards_stream_last_event\":\"" + escapeJson(g_lastActiveCardsStreamEvent) + "\",";
  s += "\"active_cards_refresh_pending\":" + String(g_activeCardsRefreshPending ? "true" : "false") + ",";
  s += "\"active_cards_loaded\":" + String((unsigned)cardsLoaded) + ",";
  s += "\"active_set_size\":" + String((unsigned)setSize) + ",";
  s += "\"activity_enabled\":" + String(g_activityEnabled ? "true" : "false") + ",";
  s += "\"last_heartbeat_ms\":" + String(g_lastHeartbeat) + ",";
  s += "\"last_flush_ms\":" + String(g_lastFlush) + ",";
  s += "\"firebase_fail_count\":" + String(firebaseFailCount) + ",";
  s += "\"ota_update_available\":" + String(g_otaUpdateAvailable ? "true" : "false") + ",";
  s += "\"available_version\":\"" + g_availableFirmwareVersion + "\",";
  s += "\"free_heap\":" + String(ESP.getFreeHeap());
  s += "}";
  portal.send(200, "application/json", s);
}

// Plain-text tail logs endpoint.
// Query params:
//   lines=<N>  -> number of recent lines (default 200, max ring size)
//   clear=1    -> clears in-memory log ring
void handleLogs() {
  if (portal.hasArg("clear")) {
    String clearArg = portal.arg("clear");
    clearArg.toLowerCase();
    if (clearArg == "1" || clearArg == "true" || clearArg == "yes") {
      clearLogDump();
      portal.send(200, "text/plain", "OK: logs cleared\n");
      return;
    }
  }

  size_t tail = LOG_DEFAULT_TAIL_LINES;
  if (portal.hasArg("lines")) {
    long req = portal.arg("lines").toInt();
    if (req > 0) tail = (size_t)req;
  }

  String body = getLogDump(tail);
  portal.send(200, "text/plain", body);
}
// ---------------------------------------------------------------------------

// ---------------- OTA Update Implementation ----------------

// Save firmware metadata after successful OTA
void saveFirmwareMeta(const String &version) {
  String meta = "{\"version\":\"" + version + "\",";
  meta += "\"updated_at\":\"" + rfc3339NowUTC() + "\",";
  meta += "\"device_id\":\"" + g_mac + "\"}";
  writeFile(FILE_FIRMWARE_META, meta);
  Serial.printf("Firmware metadata saved: version=%s\n", version.c_str());
}

// Read current firmware version from metadata file
String readFirmwareVersion() {
  if (!fileExists(FILE_FIRMWARE_META)) {
    return String(FIRMWARE_VERSION); // use compiled version if no metadata
  }
  String meta = readFile(FILE_FIRMWARE_META);
  String ver = jsonGetStr(meta, "version");
  return ver.length() ? ver : String(FIRMWARE_VERSION);
}

// OTA progress callback
void otaProgressCallback(size_t progress, size_t total) {
  static unsigned long lastPrint = 0;
  unsigned long now = millis();
  
  // Print progress every 2 seconds to avoid spam
  if (now - lastPrint > 2000 || progress == total) {
    // Guard against division by zero
    int percent = (total > 0) ? (progress * 100) / total : 0;
    Serial.printf("OTA Progress: %d%% (%u/%u bytes)\n", percent, progress, total);
    lastPrint = now;
    
    // NOTE: feedWatchdog() is skipped here because the task 
    // is unsubscribed during the OTA process to avoid lockups.
  }
}

// Perform OTA update from URL (supports HTTPS for Firebase Storage)
bool performOtaUpdate(const String &url) {
  if (url.length() == 0) {
    Serial.println("OTA: No URL provided");
    return false;
  }
  
  Serial.printf("OTA: Starting update from %s\n", url.c_str());
  g_otaInProgress = true;
  
  // Disable watchdog during OTA or extend timeout
  esp_task_wdt_delete(NULL);
  
  HTTPClient http;
  http.setTimeout(OTA_TIMEOUT_MS);
  
  // Determine if URL is HTTPS or HTTP and use appropriate client
  bool isHttps = url.startsWith("https");
  WiFiClientSecure secClient;
  WiFiClient plainClient;
  
  if (isHttps) {
    // Skip certificate verification (ESP32 has limited CA store)
    secClient.setInsecure();
    if (!http.begin(secClient, url)) {
      Serial.println("OTA: Failed to begin HTTPS connection");
      g_otaInProgress = false;
      g_otaUpdateAvailable = false;
      g_otaUpdateUrl = "";
      esp_task_wdt_add(NULL);
      return false;
    }
  } else {
    if (!http.begin(plainClient, url)) {
      Serial.println("OTA: Failed to begin HTTP connection");
      g_otaInProgress = false;
      g_otaUpdateAvailable = false;
      g_otaUpdateUrl = "";
      esp_task_wdt_add(NULL);
      return false;
    }
  }
  
  // Follow redirects (Firebase Storage may redirect)
  http.setFollowRedirects(HTTPC_STRICT_FOLLOW_REDIRECTS);
  
  int httpCode = http.GET();
  if (httpCode != HTTP_CODE_OK) {
    Serial.printf("OTA: HTTP GET failed, code=%d\n", httpCode);
    http.end();
    g_otaInProgress = false;
    g_otaUpdateAvailable = false;
    g_otaUpdateUrl = "";
    esp_task_wdt_add(NULL);
    return false;
  }
  
  int contentLength = http.getSize();
  if (contentLength <= 0) {
    Serial.println("OTA: Invalid content length");
    http.end();
    g_otaInProgress = false;
    g_otaUpdateAvailable = false;
    g_otaUpdateUrl = "";
    esp_task_wdt_add(NULL);
    return false;
  }
  
  Serial.printf("OTA: Firmware size: %d bytes\n", contentLength);
  
  // Check if we have enough space
  if (!Update.begin(contentLength)) {
    Serial.printf("OTA: Not enough space. Error=%d\n", Update.getError());
    http.end();
    g_otaInProgress = false;
    g_otaUpdateAvailable = false;
    g_otaUpdateUrl = "";
    esp_task_wdt_add(NULL);
    return false;
  }
  
  // Set progress callback
  Update.onProgress(otaProgressCallback);
  
  // Write firmware
  WiFiClient *stream = http.getStreamPtr();
  size_t written = Update.writeStream(*stream);
  
  if (written != (size_t)contentLength) {
    Serial.printf("OTA: Write failed. Written=%u Expected=%d\n", (unsigned)written, contentLength);
    Update.abort();
    http.end();
    g_otaInProgress = false;
    g_otaUpdateAvailable = false;
    g_otaUpdateUrl = "";
    esp_task_wdt_add(NULL);
    return false;
  }
  
  Serial.println("OTA: Write complete");
  
  if (!Update.end()) {
    Serial.printf("OTA: Update.end() failed. Error=%d\n", Update.getError());
    http.end();
    g_otaInProgress = false;
    g_otaUpdateAvailable = false;
    g_otaUpdateUrl = "";
    esp_task_wdt_add(NULL);
    return false;
  }
  
  if (!Update.isFinished()) {
    Serial.println("OTA: Update not finished");
    http.end();
    g_otaInProgress = false;
    g_otaUpdateAvailable = false;
    g_otaUpdateUrl = "";
    esp_task_wdt_add(NULL);
    return false;
  }
  
  http.end();
  
  Serial.println("OTA: Update successful! Rebooting...");
  
  // Save firmware metadata
  saveFirmwareMeta(g_availableFirmwareVersion.length() ? g_availableFirmwareVersion : "unknown");
  
  delay(1000);
  ESP.restart();
  
  return true; // won't reach here
}

// Check for firmware updates from Firestore
void checkForOtaUpdates(bool force = false) {
  if (g_otaInProgress) return;
  if (!(WiFi.status() == WL_CONNECTED && app.ready())) return;
  
  unsigned long now = millis();
  if (!force && (now - g_lastOtaCheck < OTA_CHECK_INTERVAL_MS)) return;
  
  g_lastOtaCheck = now;
  
  Serial.println("OTA: Checking for firmware updates...");
  
  // Query firmware metadata directly from Firestore REST.
  HTTPClient http;
  WiFiClientSecure secClient;
  secClient.setInsecure(); // TODO: replace with pinned cert in production deployments

  // Firestore REST API URL
  String url = "https://firestore.googleapis.com/v1/projects/"
             + g_projectId
             + "/databases/(default)/documents/businessess/firmware_updates";
  LOG_DEBUG("OTA metadata query URL: %s", url.c_str());

  if (!http.begin(secClient, url)) {
    LOG_ERROR("OTA metadata request failed: http.begin() returned false.");
    return;
  }

  int httpCode = http.GET();
  LOG_DEBUG("OTA metadata HTTP status=%d", httpCode);

  String payload = "";
  if (httpCode > 0) {
    payload = http.getString();
  } else {
    LOG_ERROR("OTA metadata HTTP error: %s", http.errorToString(httpCode).c_str());
    http.end();
    return;
  }
  http.end();

  if (httpCode != 200) {
    LOG_WARN("OTA metadata request returned non-200 status (%d).", httpCode);
    return;
  }

  // Parse version and URL from raw Firestore REST response
  // Firestore REST wraps values as: "fields": { "version": { "stringValue": "..." } }
  String remoteVersion = fsGetStringField(payload, "version");
  String downloadUrl = fsGetStringField(payload, "download_url");
  bool mandatory = false;
  fsGetBoolField(payload, "mandatory", mandatory);
  
  if (remoteVersion.length() == 0 || downloadUrl.length() == 0) {
    LOG_INFO("OTA metadata available but no update payload was found.");
    return;
  }
  
  String currentVersion = readFirmwareVersion();
  LOG_INFO("OTA version check: current=%s remote=%s", currentVersion.c_str(), remoteVersion.c_str());
  
  // Simple version comparison (you can implement semantic versioning)
  if (remoteVersion != currentVersion) {
    LOG_INFO("OTA update available: version %s", remoteVersion.c_str());
    g_otaUpdateAvailable = true;
    g_availableFirmwareVersion = remoteVersion;
    g_otaUpdateUrl = downloadUrl;
    
    // Auto-update if mandatory
    if (mandatory) {
      LOG_INFO("OTA update is marked mandatory. Starting now.");
      performOtaUpdate(downloadUrl);
    }
  } else {
    g_otaUpdateAvailable = false;
    g_availableFirmwareVersion = "";
  }
}

// HTTP endpoint to trigger OTA update manually
void handleOtaUpdate() {
  if (!g_otaUpdateAvailable) {
    portal.send(400, "text/plain", "No update available");
    return;
  }
  
  portal.send(200, "text/html",
    "<html><body><h3>Starting OTA Update...</h3>"
    "<p>Device will reboot after update completes.</p>"
    "<p>Do not power off the device!</p></body></html>");
  
  delay(500);
  performOtaUpdate(g_otaUpdateUrl);
}

// HTTP endpoint to check OTA status
void handleOtaCheck() {
  checkForOtaUpdates(true); // Force check on manual request
  
  String s = "{";
  s += "\"current_version\":\"" + readFirmwareVersion() + "\",";
  s += "\"update_available\":" + String(g_otaUpdateAvailable ? "true" : "false") + ",";
  s += "\"available_version\":\"" + g_availableFirmwareVersion + "\",";
  s += "\"in_progress\":" + String(g_otaInProgress ? "true" : "false");
  s += "}";
  
  portal.send(200, "application/json", s);
}

// -----------------------------------------------------------

// ---------------- Helpers for active_cards meta parsing ----------------

// Extract a timestamp-like string for a field (attempts several patterns)
String fsGetTimestampField(const String &json, const char *field) {
  // Looks for either:
  // "field": { "timestampValue": "2025-10-14T19:19:19.733Z" }
  // or top-level "updateTime": "2025-10-14T19:19:19.733Z"
  String key = "\"" + String(field) + "\"";
  int k = json.indexOf(key);
  if (k >= 0) {
    int tv = json.indexOf("\"timestampValue\"", k);
    if (tv >= 0) {
      int colon = json.indexOf(':', tv);
      if (colon >= 0) {
        int q1 = json.indexOf('"', colon);
        if (q1 >= 0) {
          int q2 = json.indexOf('"', q1+1);
          if (q2 >= 0) return json.substring(q1+1, q2);
        }
      }
    }
    // fallback: maybe field was written differently; search for first quoted value after field
    int q1 = json.indexOf('"', k + key.length());
    if (q1 >= 0) {
      int q2 = json.indexOf('"', q1+1);
      if (q2 >= 0) {
        String candidate = json.substring(q1+1, q2);
        // basic sanity: contains 'T' and '-' and ':'
        if (candidate.indexOf('T') >= 0 && candidate.indexOf(':') >= 0) return candidate;
      }
    }
  }

  // fallback: try updateTime (top-level metadata field returned by SDK)
  int ut = json.indexOf("\"updateTime\"");
  if (ut >= 0) {
    int q1 = json.indexOf('"', ut + 12);
    q1 = json.indexOf('"', q1 + 1);
    int q2 = json.indexOf('"', q1 + 1);
    if (q1 >= 0 && q2 >= 0) return json.substring(q1+1, q2);
  }

  return String("");
}

void writeAccessMeta(const String &updated_at) {
  String meta = "{\"updated_at\":\"" + updated_at + "\"}";
  writeFile(FILE_ACCESS_META, meta);
}

String normalizeDatabaseUrl(const String &rawUrl) {
  String url = trimStr(rawUrl);
  if (url.startsWith("https://")) url = url.substring(8);
  else if (url.startsWith("http://")) url = url.substring(7);
  int slash = url.indexOf('/');
  if (slash >= 0) url = url.substring(0, slash);
  while (url.endsWith("/")) url.remove(url.length() - 1);
  return trimStr(url);
}

String effectiveDatabaseUrl() {
  String configured = normalizeDatabaseUrl(g_databaseUrl);
  if (configured.length()) return configured;

  if (!g_projectId.length()) return "";

  // Backward-compatible fallback for projects with default RTDB instance naming.
  String derived = g_projectId + "-default-rtdb.firebaseio.com";
  LOG_WARN("database_url missing. Falling back to derived URL: %s", derived.c_str());
  return derived;
}

String activeCardsVersionPath() {
  return String(ACTIVE_CARDS_VERSION_ROOT) + "/" + g_businessId;
}

String parseActiveCardsVersionToken(const String &raw) {
  String token = trimStr(raw);
  if (!token.length() || token == "null") return "";

  if (token[0] == '{') {
    String v = jsonGetScalar(token, "version");
    if (!v.length()) v = jsonGetScalar(token, "updated_at");
    if (!v.length()) v = jsonGetScalar(token, "value");
    if (v.length()) token = v;
  }

  if (token.length() >= 2 && token[0] == '"' && token[token.length() - 1] == '"') {
    token = token.substring(1, token.length() - 1);
  }

  return trimStr(token);
}

void processActiveCardsVersionStream(AsyncResult &r) {
  if (!r.isResult()) return;

  if (r.isError()) {
    g_activeCardsStreamHealthy = false;
    g_lastActiveCardsStreamEventMs = millis();
    g_lastActiveCardsStreamEvent = "error";
    LOG_WARN("Active-cards stream error: %s (%d)",
             r.error().message().c_str(),
             r.error().code());
    return;
  }

  if (!r.available()) return;

  RealtimeDatabaseResult &stream = r.to<RealtimeDatabaseResult>();
  if (!stream.isStream()) return;

  String eventType = stream.event();
  g_lastActiveCardsStreamEventMs = millis();
  g_lastActiveCardsStreamEvent = eventType;

  if (eventType == "keep-alive") {
    g_activeCardsStreamHealthy = true;
    static unsigned long lastKeepAliveLogMs = 0;
    unsigned long now = millis();
    if (now - lastKeepAliveLogMs >= 60000UL) {
      LOG_DEBUG("Active-cards stream keep-alive received.");
      lastKeepAliveLogMs = now;
    }
    return;
  }

  if (eventType == "cancel" || eventType == "auth_revoked") {
    g_activeCardsStreamHealthy = false;
    LOG_WARN("Active-cards stream event=%s. Verify RTDB rules and auth state.",
             eventType.c_str());
    return;
  }

  String token = parseActiveCardsVersionToken(stream.to<String>());
  g_activeCardsStreamHealthy = true;
  if (!token.length()) {
    LOG_WARN("Active-cards stream event=%s had empty token. Scheduling refresh anyway.",
             eventType.c_str());
  } else {
    LOG_INFO("Active-cards stream event=%s token=%s",
             eventType.c_str(),
             token.c_str());
  }

  // For put/patch events we refresh even when token repeats, because some backends may
  // write stable version values while payload still changes.
  bool shouldRefresh = true;
  if (token.length() && token == g_lastActiveCardsVersionToken && eventType == "get") {
    shouldRefresh = false;
  }
  if (token.length()) g_lastActiveCardsVersionToken = token;

  if (!shouldRefresh) {
    LOG_DEBUG("Active-cards stream token unchanged on get event. Skipping refresh.");
    return;
  }

  g_activeCardsRefreshPending = true;
  g_activeCardsRefreshAt = millis() + ACTIVE_CARDS_STREAM_DEBOUNCE_MS;
  LOG_INFO("Active-cards refresh scheduled from stream event.");
}

void startActiveCardsVersionStream() {
  if (g_activeCardsStreamStarted) return;
  if (!(WiFi.status() == WL_CONNECTED && app.ready() && g_businessId.length())) return;
  if (millis() < g_nextActiveCardsStreamRestartAt) return;

  String dbUrl = effectiveDatabaseUrl();
  if (!dbUrl.length()) {
    LOG_WARN("Active-cards stream not started: database_url is missing.");
    return;
  }
  if (!g_databaseUrl.length()) {
    LOG_WARN("Using inferred database_url '%s'. Set explicit database_url in provisioning to avoid host mismatch.", dbUrl.c_str());
  }

  if (!g_lastActiveCardsVersionToken.length() && SPIFFS.exists(FILE_ACCESS_META)) {
    g_lastActiveCardsVersionToken = jsonGetStr(readFile(FILE_ACCESS_META), "updated_at");
  }

  Database.url(dbUrl);
  Database.setSSEFilters("get,put,patch,keep-alive,cancel,auth_revoked");
  streamClient.setSSEFilters("get,put,patch,keep-alive,cancel,auth_revoked");

  String path = activeCardsVersionPath();
  Database.get(streamClient, path, processActiveCardsVersionStream, true, "activeCardsVersionStream");
  g_activeCardsStreamStarted = true;
  g_activeCardsStreamHealthy = false;
  g_lastActiveCardsStreamEventMs = 0;
  g_activeCardsStreamStartedAtMs = millis();
  g_lastActiveCardsStreamEvent = "starting";
  LOG_INFO("Active-cards stream started at %s%s", dbUrl.c_str(), path.c_str());
  LOG_INFO("Waiting for active-cards stream events...");
}

void activeCardsStreamWatchdogTick() {
  if (!(WiFi.status() == WL_CONNECTED && app.ready() && g_businessId.length())) return;

  if (!g_activeCardsStreamStarted) {
    startActiveCardsVersionStream();
    return;
  }

  unsigned long now = millis();
  bool shouldRestart = false;
  const char *reason = nullptr;

  if (g_lastActiveCardsStreamEventMs == 0) {
    if (g_activeCardsStreamStartedAtMs != 0 &&
        now - g_activeCardsStreamStartedAtMs >= ACTIVE_CARDS_STREAM_FIRST_EVENT_TIMEOUT_MS) {
      shouldRestart = true;
      reason = "no initial stream event";
    }
  } else if (now - g_lastActiveCardsStreamEventMs >= ACTIVE_CARDS_STREAM_STALE_MS) {
    shouldRestart = true;
    reason = "stream stale";
  }

  if (!shouldRestart || now < g_nextActiveCardsStreamRestartAt) return;

  LOG_WARN("Restarting active-cards stream (%s).", reason);
  streamClient.stopAsync(true);
  g_activeCardsStreamStarted = false;
  g_activeCardsStreamHealthy = false;
  g_activeCardsStreamStartedAtMs = 0;
  g_lastActiveCardsStreamEvent = "restarting";
  g_nextActiveCardsStreamRestartAt = now + ACTIVE_CARDS_STREAM_RESTART_BACKOFF_MS;
  startActiveCardsVersionStream();
}

void activeCardsSyncTick() {
  if (!g_activeCardsRefreshPending) return;
  if (g_firestoreBusy) return;
  if (millis() < g_activeCardsRefreshAt) return;
  if (!(WiFi.status() == WL_CONNECTED && app.ready() && g_businessId.length())) return;

  g_activeCardsRefreshPending = false;
  fetchActiveCardsFromFirebase_v2();

  if (aClient.lastError().code() != 0) {
    g_activeCardsRefreshPending = true;
    g_activeCardsRefreshAt = millis() + ACTIVE_CARDS_STREAM_RETRY_MS;
    LOG_WARN("Active-cards refresh failed. Retrying in %lums.",
             (unsigned long)ACTIVE_CARDS_STREAM_RETRY_MS);
  } else {
    LOG_INFO("Active-cards refresh applied from stream update.");
  }
}

// Fetch active card list from Firestore and atomically refresh in-memory cache.
void fetchActiveCardsFromFirebase_v2() {
  if (g_firestoreBusy) {
    LOG_DEBUG("Skipping active card sync because Firestore is busy.");
    return;
  }

  g_firestoreBusy = true;

  bool shouldExit = false;

  // ---------- SAFETY GUARD ----------
  if (!(WiFi.status() == WL_CONNECTED && app.ready() && g_businessId.length())) {
    LOG_DEBUG("Skipping active card sync (offline, app not ready, or business_id missing).");
    shouldExit = true;
  }

  if (!shouldExit) {
    String docPath = "businessess/" + g_businessId + "/active_cards/active_cards";
    LOG_INFO("Syncing active cards from Firestore.");

    String payload = Docs.get(
      aClient,
      Firestore::Parent(g_projectId),
      docPath,
      GetDocumentOptions()
    );

    if (aClient.lastError().code() != 0) {
      LOG_WARN("Active card sync failed: %s", aClient.lastError().message().c_str());
      shouldExit = true;
    }

    if (!shouldExit) {
      // ---- extract updated_at ----
      String remoteUpdated = fsGetTimestampField(payload, "updated_at");

      String localUpdated = "";
      if (SPIFFS.exists(FILE_ACCESS_META)) {
        localUpdated = jsonGetStr(readFile(FILE_ACCESS_META), "updated_at");
      }

      // No change in metadata: skip full payload parsing/write.
      if (remoteUpdated.length() &&
          localUpdated.length() &&
          remoteUpdated == localUpdated) {

        LOG_DEBUG("Active card metadata unchanged. Skipping update.");
        shouldExit = true;
      }

      if (!shouldExit) {
        // ---- parse cards ----
        std::vector<ActiveCardEntry> newList;
        int pos = 0;

        while (true) {
          int k = payload.indexOf("\"card_data\"", pos);
          if (k < 0) break;

          String card = fsGetStringField(payload.substring(k), "card_data");
          String st   = fsGetStringField(payload.substring(k), "start_time");
          String et   = fsGetStringField(payload.substring(k), "end_time");

          if (!st.length()) st = "00:00";
          if (!et.length()) et = "23:59";

          newList.push_back({card, st, et});
          pos = k + 10;
          if (newList.size() >= MAX_ACTIVE_CARDS) break;
        }

        // ---- ATOMIC SWAP ----
        if (xSemaphoreTake(g_activeCardsMutex, portMAX_DELAY) == pdTRUE) {
          g_activeCards.clear();
          g_activeSet.clear();

          for (auto &e : newList) {
            g_activeCards.push_back(e);
            String n = normalizeUidStr(e.card_data);
            if (n.length()) g_activeSet.insert(std::string(n.c_str()));
          }
          xSemaphoreGive(g_activeCardsMutex);
        }

        // ---- persist ----
        String out = "[";
        for (size_t i = 0; i < newList.size(); ++i) {
          if (i) out += ",";
          out += "{\"card_data\":\"" + newList[i].card_data +
                 "\",\"start_time\":\"" + newList[i].start_time +
                 "\",\"end_time\":\"" + newList[i].end_time + "\"}";
        }
        out += "]";
        writeFile(FILE_ACCESS_LIST, out);

        writeAccessMeta(remoteUpdated.length() ? remoteUpdated : rfc3339NowUTC());

        LOG_INFO("Active card sync complete: %u cards loaded.", (unsigned)newList.size());
      }
    }
  }

  // ---------- ALWAYS RELEASE ----------
  g_firestoreBusy = false;
}

// Find the first matching shift window for a UID.
// Checks ALL entries for a UID and can test against a target HH:MM.
// If checkTargetHHMM is non-empty it returns the first matching shift that contains the target time.
// Otherwise it returns the first shift found for that UID (for diagnostics).
bool findActiveCardTimes(const String &uid, const String &checkTargetHHMM, String &outStart, String &outEnd) {
  String nuid = normalizeUidStr(uid);
  if (nuid.length() == 0) return false;

  // Read active-card list under mutex.
  bool anyFound = false;
  bool matchedShift = false;
  if (xSemaphoreTake(g_activeCardsMutex, portMAX_DELAY) == pdTRUE) {
    for (auto &e : g_activeCards) {
      String ne = normalizeUidStr(e.card_data);
      if (ne == nuid) {
        anyFound = true;
        if (checkTargetHHMM.length()) {
          if (timeInRange(e.start_time, e.end_time, checkTargetHHMM)) {
            outStart = e.start_time;
            outEnd    = e.end_time;
            matchedShift = true;
            break;
          }
        } else {
          outStart = e.start_time;
          outEnd   = e.end_time;
          matchedShift = true;
          break;
        }
      }
    }

    // If at least one entry exists but none matched the target, return first entry (for diagnostics) but report false.
    if (anyFound && !matchedShift) {
      for (auto &e : g_activeCards) {
        String ne = normalizeUidStr(e.card_data);
        if (ne == nuid) {
          outStart = e.start_time;
          outEnd    = e.end_time;
          break;
        }
      }
    }
    xSemaphoreGive(g_activeCardsMutex);
  }
  return matchedShift;
}
// ----------------------------------------------------------------------------

// ---------------- Provisioning portal functions (startProvisionPortal/stopProvisionPortal) ----------------
void ensurePortalServerRunning() {
  // Register handlers only once.
  if (!g_portalHandlersRegistered) {
    portal.on("/", HTTP_GET, handleRoot);
    portal.on("/save", HTTP_POST, handleSave);
    portal.on("/reboot", HTTP_GET, handleReboot);
    portal.on("/status", HTTP_GET, handleStatus); // diagnostics endpoint
    portal.on("/logs", HTTP_GET, handleLogs); // serial/runtime logs
    portal.on("/ota/check", HTTP_GET, handleOtaCheck); // check for OTA updates
    portal.on("/ota/update", HTTP_POST, handleOtaUpdate); // trigger OTA update
    g_portalHandlersRegistered = true;
  }

  // WebServer must not start before Wi-Fi driver mode is initialized.
  if (WiFi.getMode() == WIFI_MODE_NULL) {
    return;
  }

  // Keep web server alive regardless of AP state. AP is controlled separately.
  if (!g_portalServerStarted) {
    portal.begin();
    g_portalServerStarted = true;
    LOG_INFO("Provisioning web server started.");
  }
}

void startProvisionPortal(unsigned long forcedWindowMs) {
  if (g_portalActive) {
    // already active: if a new forced window was requested, extend it
    if (forcedWindowMs > 0) {
      g_forcedProvUntil = millis() + forcedWindowMs;
      Serial.printf("Extending forced provisioning window until +%lus\n", forcedWindowMs / 1000);
    }
    return;
  }

  Serial.println("\n*** Starting Provisioning Portal ***");

  // Ensure Wi-Fi is in AP+STA so we can serve the portal while STA tries reconnecting
  WiFi.mode(WIFI_AP_STA);
  WiFi.softAP(apSSID().c_str(), AP_PASS);
  kickWifiReconnect(true);
  delay(200);

  ensurePortalServerRunning();

  // bookkeeping
  g_portalActive = true;
  g_inProvWindow = true;
  g_provWindowStart = millis();

  if (forcedWindowMs > 0) {
    g_forcedProvUntil = millis() + forcedWindowMs;
    // clear transient firebase failures so operator is not repeatedly bounced back
    firebaseFailCount = 0;
    firebaseLastFailAt = 0;
    Serial.printf("Provisioning portal FORCED for %lu seconds.\n", forcedWindowMs / 1000);
  } else {
    g_forcedProvUntil = 0;
    Serial.println("Provisioning portal started (normal window).");
  }

  // restart lookup timer after portal ends (fresh lookup window)
  g_wifiFailStart = 0;

  LOG_INFO("Provisioning mode active.");
  LOG_INFO("Connect to AP SSID: %s", apSSID().c_str());
  LOG_INFO("Provisioning portal URL: http://%s", WiFi.softAPIP().toString().c_str());
  LOG_INFO("Endpoints: /  /status  /logs  /save  /reboot  /ota/check  /ota/update");
}

void stopProvisionPortal() {
  if (!g_portalActive) return;
  Serial.println("Stopping provisioning portal...");
  WiFi.softAPdisconnect(true);     // stop AP
  WiFi.mode(WIFI_STA);
  kickWifiReconnect(true);

  g_portalActive = false;
  // Clear provisioning state so recovery logic behaves consistently.
  g_inProvWindow = false;
  g_provWindowStart = 0;
  delay(50); // let WiFi driver settle
}

// ---------------- Non-blocking relay control functions ----------------
// Start a relay pulse (non-blocking)
void relayStartPulse() {
  if (g_relayState == RELAY_IDLE) {
    digitalWrite(RELAY, RELAY_UNLOCKED_STATE);
    g_relayState = RELAY_PULSING;
    g_relayPulseStartMs = millis();
    Serial.println("Relay: pulse started (non-blocking)");
  }
}

// Update relay state machine (call from loop)
void relayUpdate() {
  if (g_relayState == RELAY_PULSING) {
    if (millis() - g_relayPulseStartMs >= DOOR_PULSE_MS) {
      digitalWrite(RELAY, RELAY_LOCKED_STATE);
      g_relayState = RELAY_IDLE;
      Serial.println("Relay: pulse ended, locked");
    }
  }
}
// -------------------------------------------------------------------------------

// ---------------- Firebase async callback (with light backoff scheduling) ----------------
void asyncCB(AsyncResult &r) {
  // EVENT-level messages (SDK high-level events)
  if (r.isEvent()) {
    String msg = r.appEvent().message();
    int code = r.appEvent().code();
    Firebase.printf("[Evt] %s (%d)\n", msg.c_str(), code);

    String lmsg = msg;
    lmsg.toLowerCase();

    // Treat 400/Bad Request as auth/config failure
    if (lmsg.indexOf("400") >= 0 || lmsg.indexOf("bad request") >= 0 || lmsg.indexOf("permission_denied") >= 0) {
      firebaseFailCount++;
      firebaseLastFailAt = millis();
      // Use named constants for backoff calculation
      int exp = firebaseFailCount;
      if (exp > FIREBASE_BACKOFF_MAX_EXP) exp = FIREBASE_BACKOFF_MAX_EXP;
      unsigned long backoff = (1UL << exp) * FIREBASE_BACKOFF_BASE_MS;
      if (backoff > FIREBASE_BACKOFF_MAX_MS) backoff = FIREBASE_BACKOFF_MAX_MS;
      g_nextFirebaseInitAttempt = millis() + backoff;
      Serial.printf("Firebase failure #%d detected (msg: %s). Backing off %lums\n", firebaseFailCount, msg.c_str(), backoff);
    } else if (lmsg.indexOf("authenticated") >= 0 || lmsg.indexOf("auth success") >= 0 || code == 0) {
      if (firebaseFailCount > 0) {
        LOG_INFO("Firebase auth success - clearing failure counter.");
      }
      firebaseFailCount = 0;
      firebaseLastFailAt = 0;
    }
  }
  // ERRORS surfaced by SDK (lower-level)
  if (r.isError()) {
    String em = r.error().message();
    int ec = r.error().code();
    Firebase.printf("[Err] %s (%d)\n", em.c_str(), ec);

    String lem = em;
    lem.toLowerCase();
    if (lem.indexOf("400") >= 0 || lem.indexOf("bad request") >= 0 || lem.indexOf("permission_denied") >= 0) {
      firebaseFailCount++;
      firebaseLastFailAt = millis();
      // Use named constants for backoff calculation
      int exp = firebaseFailCount;
      if (exp > FIREBASE_BACKOFF_MAX_EXP) exp = FIREBASE_BACKOFF_MAX_EXP;
      unsigned long backoff = (1UL << exp) * FIREBASE_BACKOFF_BASE_MS;
      if (backoff > FIREBASE_BACKOFF_MAX_MS) backoff = FIREBASE_BACKOFF_MAX_MS;
      g_nextFirebaseInitAttempt = millis() + backoff;
      Serial.printf("Firebase error counted as failure #%d (%s). Backing off %lums\n", firebaseFailCount, em.c_str(), backoff);
    }
  }
  // PAYLOAD (if any data is available)
  if (r.available()) {
    Firebase.printf("[Payload] %s\n", r.c_str());
  }
}

// ---------------- Firebase init (DEV TLS: insecure)
// Start firebase initialization (non-blocking)
void firebaseInitStart() {
  if (g_firebaseInitInProgress) return;
  Serial.println("Starting Firebase init (non-blocking start).");
  user_auth = UserAuth(g_apiKey, g_userEmail, g_userPass);
  ssl_client.setInsecure(); // dev path
  stream_ssl_client.setInsecure(); // dev path for RTDB stream client
  initializeApp(aClient, app, getAuth(user_auth), 30000, asyncCB);

  g_firebaseInitInProgress = true;
  g_firebaseInitStartedAt = millis();
  g_lastFirebasePoll = 0;
}

// Poll firebase initialization - call from loop() frequently but non-blocking
void firebaseInitPoll() {
  if (!g_firebaseInitInProgress) return;

  unsigned long now = millis();

  // Give SDK a short slice to progress
  if (now - g_lastFirebasePoll >= FIREBASE_INIT_POLL_SLICE_MS) {
    g_lastFirebasePoll = now;
    app.loop();
    Docs.loop();
  }

  // If ready, finish initialization
  if (app.ready()) {
    Serial.println("Firebase init completed (app.ready()).");
    app.getApp<Firestore::Documents>(Docs);
    app.getApp<RealtimeDatabase>(Database);
    g_firebaseInitInProgress = false;
    firebaseFailCount = 0;
    firebaseLastFailAt = 0;

    // Fetch active_cards immediately so device has up-to-date list at boot/ready
    fetchActiveCardsFromFirebase_v2();

    // Force a clean RTDB stream after auth is confirmed ready.
    if (g_activeCardsStreamStarted) {
      LOG_INFO("Restarting active-cards stream after Firebase ready.");
      streamClient.stopAsync(true);
      g_activeCardsStreamStarted = false;
      g_activeCardsStreamHealthy = false;
      g_activeCardsStreamStartedAtMs = 0;
      g_lastActiveCardsStreamEvent = "restarting_after_ready";
    }
    startActiveCardsVersionStream();

    // If provisioning portal is active, close it now that Firebase is good
    if (g_portalActive) {
      LOG_INFO("Firebase ready - stopping provisioning portal.");
      stopProvisionPortal();
      g_forcedProvUntil = 0;
      g_inProvWindow = false;
      g_provWindowStart = 0;
    }
    return;
  }

  // Timeout for this attempt
  if (now - g_firebaseInitStartedAt >= FIREBASE_INIT_POLL_TIMEOUT_MS) {
    Serial.println("Firebase init attempt timed out (poll). Marking init as failed for now.");
    g_firebaseInitInProgress = false;
    // Record failure
    firebaseFailCount++;
    firebaseLastFailAt = now;
    // Backoff next attempt to allow TCP/IP/wifi to settle
    // Larger backoff if repeated failures
    int exp = firebaseFailCount;
    if (exp > FIREBASE_BACKOFF_MAX_EXP) exp = FIREBASE_BACKOFF_MAX_EXP;
    unsigned long backoff = (1UL << exp) * FIREBASE_BACKOFF_BASE_MS;
    if (backoff > FIREBASE_BACKOFF_MAX_MS) backoff = FIREBASE_BACKOFF_MAX_MS;
    g_nextFirebaseInitAttempt = now + backoff;
    Serial.printf("Next Firebase init attempt delayed for %lums.\n", backoff);
  }
}

// Called early from loop() to decide whether to force provisioning AP when Firebase auth keeps failing.
void checkFirebaseFailureState() {
  if (firebaseFailCount == 0) return;

  // expire stale failures
  if (firebaseLastFailAt != 0 && millis() - firebaseLastFailAt > FIREBASE_FAIL_RESET_MS) {
    LOG_INFO("Firebase failures expired - resetting counter.");
    firebaseFailCount = 0;
    firebaseLastFailAt = 0;
    return;
  }

  if (firebaseFailCount >= FIREBASE_FAIL_THRESHOLD && !g_portalActive) {
    Serial.printf("Firebase failures >= %d - forcing provisioning AP for %lu seconds.\n",
                  FIREBASE_FAIL_THRESHOLD, FIREBASE_PROVISION_WINDOW_MS / 1000);

    startProvisionPortal(FIREBASE_PROVISION_WINDOW_MS);

    // mark provisioning window active so wifiRecoveryTick uses the forced expiry
    g_inProvWindow = true;
    g_provWindowStart = millis();

    // Clear failure counter so we don't re-trigger repeatedly
    firebaseFailCount = 0;
    firebaseLastFailAt = 0;
  }
}

// ---------------- Firestore JSON helpers (whitespace tolerant)
// Looks for: "<field>": { "stringValue": "<VALUE>" }
String fsGetStringField(const String &json, const String &field) {
  String key="\"" + field + "\"";
  int k=json.indexOf(key);
  if(k<0) return "";
  int sv=json.indexOf("\"stringValue\"",k);
  if(sv<0) {
    // fallback to first quoted string after key
    int q1=json.indexOf('"',k+key.length());
    if(q1<0) return "";
    int q2=json.indexOf('"',q1+1);
    if(q2<0) return "";
    return json.substring(q1+1,q2);
  }
  int colon=json.indexOf(':',sv); if(colon<0) return "";
  int q1=colon+1;
  while (q1 < (int)json.length() && (json[q1]==' '||json[q1]=='\n'||json[q1]=='\r'||json[q1]=='\t')) q1++;
  q1 = json.indexOf('"', q1); if(q1<0) return "";
  int q2=json.indexOf('"', q1 + 1); if(q2<0) return "";
  return json.substring(q1 + 1, q2);
}
// Looks for: "<field>": { "booleanValue": true|false }
bool fsGetBoolField(const String &json, const char *field, bool &out) {
  String key = "\"" + String(field) + "\"";
  int k = json.indexOf(key);
  if (k < 0) return false;
  int bv = json.indexOf("\"booleanValue\"", k);
  if (bv < 0) {
    // fallback: find ": true" or ": false" after field
    int colon = json.indexOf(':', k);
    if (colon < 0) return false;
    int p = colon + 1;
    while (p < (int)json.length() && (json[p]==' '||json[p]=='\n'||json[p]=='\r'||json[p]=='\t')) p++;
    if (json.startsWith("true", p))  { out = true;  return true; }
    if (json.startsWith("false", p)) { out = false; return true; }
    return false;
  }
  int colon = json.indexOf(':', bv); if (colon < 0) return false;
  int p = colon + 1;
  while (p < (int)json.length() && (json[p]==' '||json[p]=='\n'||json[p]=='\r'||json[p]=='\t')) p++;
  if (json.startsWith("true", p))  { out = true;  return true; }
  if (json.startsWith("false", p)) { out = false; return true; }
  return false;
}

// ---------------- Device registration / upserts
bool fsUpsertRootDevice() {
  if (!app.ready()) return false;

  const String docPath = "devices/" + g_mac;

  Document<Values::Value> d;
  d.add("device_id",   Values::Value(Values::StringValue(g_mac)));
  d.add("business_id", Values::Value(Values::StringValue(g_businessId)));
  d.add("device_status",
        Values::Value(Values::StringValue("online")));
  d.add("updated_by",
        Values::Value(Values::StringValue(g_userEmail)));

  // Safe timestamps (skips unset clock values).
  safeAddTimestamp(d, "device_last_online");
  safeAddTimestamp(d, "updated_at");

  // Try CREATE
  (void)Docs.createDocument(
    aClient,
    Firestore::Parent(g_projectId),
    docPath,
    DocumentMask(),
    d
  );

  if (aClient.lastError().code() == 0) return true;

  // Fallback to PATCH
  PatchDocumentOptions patchOpts{DocumentMask(), DocumentMask(), Precondition()};
  (void)Docs.patch(
    aClient,
    Firestore::Parent(g_projectId),
    docPath,
    patchOpts,
    d
  );

  return aClient.lastError().code() == 0;
}

bool fsUpsertBusinessDevice() {
  if (!app.ready() || g_businessId.length() == 0) return false;

  const String docPath =
    "businessess/" + g_businessId + "/business_devices/" + g_mac;

  // ---------- CREATE ----------
  Document<Values::Value> c;
  c.add("device_id",          Values::Value(Values::StringValue(g_mac)));
  c.add("device_business_id", Values::Value(Values::StringValue(g_businessId)));
  c.add("device_status",      Values::Value(Values::BooleanValue(true)));
  c.add("device_network_ssid",
        Values::Value(Values::StringValue(g_ssid)));
  c.add("device_network_password",
        Values::Value(Values::StringValue(g_pass)));
  c.add("device_name",
        Values::Value(Values::StringValue(g_deviceName)));
  c.add("updated_by",
        Values::Value(Values::StringValue(g_userEmail)));

  // Safe timestamps (skips unset clock values).
  safeAddTimestamp(c, "device_last_online");
  safeAddTimestamp(c, "updated_at");
  safeAddTimestamp(c, "created_at");

  (void)Docs.createDocument(
    aClient,
    Firestore::Parent(g_projectId),
    docPath,
    DocumentMask(),
    c
  );

  if (aClient.lastError().code() == 0) return true;

  // ---------- PATCH ----------
  Document<Values::Value> p;
  p.add("device_id",          Values::Value(Values::StringValue(g_mac)));
  p.add("device_business_id", Values::Value(Values::StringValue(g_businessId)));
  p.add("device_status",      Values::Value(Values::BooleanValue(true)));
  p.add("device_network_ssid",
        Values::Value(Values::StringValue(g_ssid)));
  p.add("device_network_password",
        Values::Value(Values::StringValue(g_pass)));
  p.add("device_name",
        Values::Value(Values::StringValue(g_deviceName)));
  p.add("updated_by",
        Values::Value(Values::StringValue(g_userEmail)));

  // Safe timestamps (skips unset clock values).
  safeAddTimestamp(p, "device_last_online");
  safeAddTimestamp(p, "updated_at");

  PatchDocumentOptions patchOpts{DocumentMask(), DocumentMask(), Precondition()};
  (void)Docs.patch(
    aClient,
    Firestore::Parent(g_projectId),
    docPath,
    patchOpts,
    p
  );

  return aClient.lastError().code() == 0;
}

// Device registration with error logging
bool fsRegisterOrUpdateDevice() {
  if (g_firestoreBusy) return false;
  g_firestoreBusy = true;

  bool ok1 = fsUpsertRootDevice();
  bool ok2 = (g_businessId.length()==0) ? true : fsUpsertBusinessDevice();

  g_firestoreBusy = false;
  return ok1 && ok2;
}

// ---------------- Activity flag (read device_status from business_devices)
bool readActivityCache() {
  String s = readFile(FILE_ACTIVITY);
  if (s == "0") { g_activityEnabled = false; return true; }
  if (s == "1") { g_activityEnabled = true;  return true; }
  return false;
}
void writeActivityCache(bool on) { writeFile(FILE_ACTIVITY, on ? "1" : "0"); }

// NOTE: you asked to read device_status (not activity_status). Using device_status from business_devices doc.
bool fsFetchActivityStatus(bool &activeOut) {
  activeOut = true;
  if (!app.ready() || g_businessId.length() == 0) return false;
  String docPath = "businessess/" + g_businessId + "/business_devices/" + g_mac;
  String payload = Docs.get(aClient, Firestore::Parent(g_projectId), docPath, GetDocumentOptions());
  if (aClient.lastError().code() != 0) return false;

  bool v=false;
  // try device_status first (boolean)
  if (fsGetBoolField(payload, "device_status", v)) { activeOut = v; return true; }
  // fallback to other names for compatibility
  if (fsGetBoolField(payload, "activity_status", v) ||
      fsGetBoolField(payload, "device_activity_status", v) ||
      fsGetBoolField(payload, "active", v)) {
    activeOut = v;
    return true;
  }
  return false;
}

void applyActivityRelay() {
  // g_activityEnabled == true  -> normal mode: locked
  // g_activityEnabled == false -> held unlocked
  digitalWrite(RELAY, g_activityEnabled ? RELAY_LOCKED_STATE : RELAY_UNLOCKED_STATE);
}

// ---------------- Card cache & fetch (unchanged)
bool cacheReadLast(const String &cardId, /*out*/ bool &okOut, /*out*/ uint32_t &ageSecOut) {
  okOut = false; ageSecOut = 0;
  File f = SPIFFS.open(FILE_CARD_CACHE, "r");
  if (!f) return false;

  String lastLine;
  while (f.available()) {
    String line = f.readStringUntil('\n');
    int p = line.indexOf("\"id\":\"");
    if (p >= 0) {
      int e = line.indexOf('\"', p+6);
      if (e > p && line.substring(p+6, e) == cardId) lastLine = line;
    }
  }
  f.close();
  if (lastLine.length() == 0) return false;

  int okPos = lastLine.indexOf("\"ok\":");
  int tsPos = lastLine.indexOf("\"ts\":");
  if (okPos < 0 || tsPos < 0) return false;

  bool ok = lastLine.substring(okPos+5).startsWith("true");
  uint32_t ts = lastLine.substring(tsPos+5).toInt();
  uint32_t nowSec = time(nullptr);
  okOut = ok; ageSecOut = (nowSec > ts) ? (nowSec - ts) : 0;
  return true;
}
void cacheUpsert(const String &cardId, bool ok) {
  File f = SPIFFS.open(FILE_CARD_CACHE, "a");
  if (!f) return;
  uint32_t nowSec = time(nullptr);
  f.printf("{\"id\":\"%s\",\"ok\":%s,\"ts\":%u}\n", cardId.c_str(), ok ? "true" : "false", nowSec);
  f.close();
}
bool fsFetchCardStatus(const String &businessId, const String &cardId, /*out*/ bool &statusOut) {
  if (!app.ready() || businessId.length()==0) return false;
  String docPath = "businessess/" + businessId + "/cards/" + cardId;
  String payload = Docs.get(aClient, Firestore::Parent(g_projectId), docPath, GetDocumentOptions());
  if (aClient.lastError().code() != 0) return false;

  bool v=false;
  if (fsGetBoolField(payload, "card_status", v)) { statusOut = v; return true; }
  statusOut = false; return true; // missing -> treat as denied (but still log)
}
bool cardStatusCacheFirst(const String &cardId, /*out*/ bool &status) {
  bool found=false, cachedOK=false; uint32_t ageSec=0;
  found = cacheReadLast(cardId, cachedOK, ageSec);

  if (found && ageSec <= CARD_CACHE_TTL_SEC) { status = cachedOK; return true; }

  if (WiFi.status() == WL_CONNECTED && app.ready() && g_businessId.length()) {
    bool fresh=false;
    if (fsFetchCardStatus(g_businessId, cardId, fresh)) {
      cacheUpsert(cardId, fresh);
      status = fresh;
      return true;
    }
  }

  if (found) { status = cachedOK; return true; } // stale fallback
  status = false; return true;                   // miss+offline/unknown -> deny-on-entry
}

// ---------------- Access logs helpers (device name + card meta)
String fsGetDeviceName(const String &businessId, const String &mac) {
  // Return configured device name as default if DB access fails
  if (!app.ready() || businessId.length()==0) 
    return g_deviceName.length() ? g_deviceName : String("Main Gate");
  String docPath = "businessess/" + businessId + "/business_devices/" + mac;
  String payload = Docs.get(aClient, Firestore::Parent(g_projectId), docPath, GetDocumentOptions());
  if (aClient.lastError().code() != 0) 
    return g_deviceName.length() ? g_deviceName : String("Main Gate");
  String n = fsGetStringField(payload, "device_name");
  return n.length() ? n : (g_deviceName.length() ? g_deviceName : String("Main Gate"));
}
void fsGetCardMeta(const String &businessId, const String &cardId, String &entityId, String &entityType) {
  entityId = ""; entityType = "";
  if (!app.ready() || businessId.length() == 0) return;

  String docPath = "businessess/" + businessId + "/cards/" + cardId;
  String payload = Docs.get(aClient, Firestore::Parent(g_projectId), docPath, GetDocumentOptions());
  if (aClient.lastError().code() != 0) return;

  entityId   = fsGetStringField(payload, "card_assigned_to");
  entityType = fsGetStringField(payload, "card_assigned_type");

  // Fallbacks for schema variants
  if (entityId == "")   entityId   = fsGetStringField(payload, "entity_id");
  if (entityType == "") entityType = fsGetStringField(payload, "card_assigned_to_type");
  if (entityType == "") entityType = fsGetStringField(payload, "entity_type");
}

// ---------------- Access logs: sequential LOG_N (with collision check)
uint32_t readLogCounter() { String s = readFile(FILE_LOG_COUNTER); return s.length() ? (uint32_t)s.toInt() : 0; }
void     writeLogCounter(uint32_t n) { writeFile(FILE_LOG_COUNTER, String(n)); }

// Let Firestore auto-generate document IDs for access logs
bool tryCreateAccessLogDocAuto(const String &parentCollectionPath,
                               const Document<Values::Value> &doc,
                               /*out*/ String &createdDocPayload) {
  createdDocPayload = "";

  // Create document without specifying an ID
  String payload = Docs.createDocument(aClient, Firestore::Parent(g_projectId),
                                       parentCollectionPath,
                                       DocumentMask(),
                                       (Document<Values::Value>&)doc);
  int err = aClient.lastError().code();

  if (err == 0) {
    // Success - Firestore generated a random ID.
    createdDocPayload = payload;
    return true;
  }

  Serial.printf("Create LOG (auto-id) transport error: %s\n",
                aClient.lastError().message().c_str());
  return false;
}

bool fsCreateAccessLogAuto(const String &businessId,
                           const String &cardHex,
                           bool granted,
                           const String &logType) {
  if (!app.ready() || businessId.length() == 0) return false;

  String entityId, entityType;
  fsGetCardMeta(businessId, cardHex, entityId, entityType);
  String devName = fsGetDeviceName(businessId, g_mac);

  Document<Values::Value> log;
  log.add("log_business_id",
          Values::Value(Values::StringValue(businessId)));
  log.add("log_card_data",
          Values::Value(Values::StringValue(cardHex)));
  log.add("log_device_id",
          Values::Value(Values::StringValue(g_mac)));
  log.add("log_device_name",
          Values::Value(Values::StringValue(devName)));
  log.add("log_entity_id",
          Values::Value(Values::StringValue(entityId)));
  log.add("log_entity_type",
          Values::Value(Values::StringValue(entityType)));
  log.add("log_status",
          Values::Value(
            Values::StringValue(granted ? "granted" : "denied")));
  log.add("log_type",
          Values::Value(Values::StringValue(logType)));

  // Safe timestamp (skips unset clock values).
  safeAddTimestamp(log, "log_timestamp");

  const String collectionPath =
    "businessess/" + businessId + "/access_logs";

  String createdPayload;
  return tryCreateAccessLogDocAuto(
    collectionPath,
    log,
    createdPayload
  );
}

// Load access list with proper mutex protection
void loadAccessListFromFile() {
  if (!fileExists(FILE_ACCESS_LIST)) {
    Serial.println("No access_list.json found on SPIFFS.");
    return;
  }

  // Protect file read with mutex
  String data;
  if (xSemaphoreTake(g_fileMutex, portMAX_DELAY) == pdTRUE) {
    data = readFile(FILE_ACCESS_LIST);
    xSemaphoreGive(g_fileMutex);
  }

  if (data.length() == 0) {
    Serial.println("access_list.json empty.");
    return;
  }

  std::vector<ActiveCardEntry> newList;
  int pos = 0;
  while (pos < (int)data.length()) {
    int cdKey = data.indexOf("\"card_data\"", pos);
    if (cdKey < 0) break;

    int vq1 = data.indexOf('"', cdKey + 11);
    vq1 = data.indexOf('"', vq1 + 1);
    int vq2 = data.indexOf('"', vq1 + 1);
    if (vq1 < 0 || vq2 < 0) break;
    String card = data.substring(vq1 + 1, vq2);

    String st = "00:00";
    int stKey = data.indexOf("\"start_time\"", cdKey);
    if (stKey >= 0) {
      int a = data.indexOf('"', stKey + 12);
      a = data.indexOf('"', a + 1);
      int b = data.indexOf('"', a + 1);
      if (a >= 0 && b > a) st = data.substring(a + 1, b);
    }

    String et = "23:59";
    int etKey = data.indexOf("\"end_time\"", cdKey);
    if (etKey >= 0) {
      int a = data.indexOf('"', etKey + 10);
      a = data.indexOf('"', a + 1);
      int b = data.indexOf('"', a + 1);
      if (a >= 0 && b > a) et = data.substring(a + 1, b);
    }

    ActiveCardEntry e;
    e.card_data = card;
    e.start_time = st;
    e.end_time = et;
    newList.push_back(e);

    pos = vq2 + 1;
    if (newList.size() >= MAX_ACTIVE_CARDS) break;
  }

  // Atomic update of active cards with proper Mutex protection
  if (xSemaphoreTake(g_activeCardsMutex, portMAX_DELAY) == pdTRUE) {
    g_updatingActiveCards = true;
    g_activeCards.clear();
    g_activeSet.clear();

    for (auto &e : newList) {
      g_activeCards.push_back(e);
      // store normalized uppercase hex
      String norm = normalizeUidStr(e.card_data);
      if (norm.length()) {
        std::string s(norm.c_str());
        g_activeSet.insert(s);  // case-insensitive match simplified by normalization
      }
    }
    g_updatingActiveCards = false;
    xSemaphoreGive(g_activeCardsMutex);
  }

  Serial.printf("Loaded %u active cards from %s\n", (unsigned)g_activeCards.size(), FILE_ACCESS_LIST);
}

// ---------------- Access logs: local queue (process-first-N, keep rest)
// Queue access log with memory optimization and error handling
void queueAccessLog(const String &businessId, const String &cardId, bool granted, const String &logType) {
  // Append a compact JSON line; upload is skipped until config complete.
  String s;
  s.reserve(100); // pre-allocate to avoid fragmentation
  s = "{";
  s += "\"bid\":\"" + businessId + "\","; 
  s += "\"card\":\"" + cardId + "\","; 
  s += "\"granted\":" + String(granted ? "true" : "false") + ","; 
  s += "\"type\":\"" + logType + "\"";
  s += "}";
  
  bool ok = appendLine(FILE_LOG_QUEUE, s);
  if (!ok) {
    Serial.println("ERROR: Failed to queue access log");
  }
}

// Thread-safe queue flush - read file under mutex, then process without mutex
void flushQueueBatch(size_t maxLines) {
  if (!(WiFi.status() == WL_CONNECTED && app.ready() && g_businessId.length())) return;
  
  // Read all lines under mutex, then release before network calls
  std::vector<String> linesToProcess;
  std::vector<String> linesToKeep;
  
  if (xSemaphoreTake(g_fileMutex, portMAX_DELAY) == pdTRUE) {
    File src = SPIFFS.open(FILE_LOG_QUEUE, "r");
    if (!src) {
      xSemaphoreGive(g_fileMutex);
      return;
    }

    Serial.println("Flushing access_logs queue...");
    size_t lineCount = 0;
    
    while (src.available()) {
      String line = src.readStringUntil('\n');
      line.trim();
      if (line.length() == 0) continue;
      
      if (lineCount < maxLines) {
        linesToProcess.push_back(line);
      } else {
        linesToKeep.push_back(line);
      }
      lineCount++;
    }
    src.close();
    xSemaphoreGive(g_fileMutex);
  }
  
  // Process lines WITHOUT holding mutex (network calls happen here)
  int sent = 0;
  for (const String &line : linesToProcess) {
    String bid = jsonGetStr(line, "bid");
    String cid = jsonGetStr(line, "card");
    String type = jsonGetStr(line, "type");
    bool granted = (line.indexOf("\"granted\":true") >= 0);

    // If queued without BID (pre-provision), use current config BID if available; else keep.
    String effectiveBID = (bid.length() ? bid : g_businessId);
    if (effectiveBID.length() == 0) {
      linesToKeep.push_back(line); // still not configured; keep it
    } else {
      bool ok = fsCreateAccessLogAuto(effectiveBID, cid, granted, type);
      if (ok) sent++;
      else    linesToKeep.push_back(line);
    }
  }
  
  // Write remaining lines back under mutex
  if (xSemaphoreTake(g_fileMutex, portMAX_DELAY) == pdTRUE) {
    String keep = "";
    for (const String &line : linesToKeep) {
      keep += line + "\n";
    }
    writeFile(FILE_LOG_QUEUE, keep);
    xSemaphoreGive(g_fileMutex);
  }
  
  Serial.printf("Flushed %d/%d logs.\n", sent, (int)linesToProcess.size());
}

// Improved flush logic with queue size monitoring
void flushQueueIfNeeded() {
  unsigned long now = millis();
  bool timeToFlush = (now - g_lastFlush > FLUSH_INTERVAL_MS);
  bool fsAlmostFull = (fsUsage() >= FLUSH_FS_THRESH);
  // Also flush if queue has too many lines (check less frequently to avoid overhead)
  static unsigned long lastQueueCheck = 0;
  size_t queueLines = 0;
  bool queueTooLarge = false;
  
  if (now - lastQueueCheck > 30000) { // check queue size every 30 seconds
    queueLines = countLogQueueLines();
    queueTooLarge = (queueLines >= MAX_LOG_QUEUE_LINES);
    lastQueueCheck = now;
  }
  
  if (timeToFlush || fsAlmostFull || queueTooLarge) {
    if (queueTooLarge) {
      Serial.printf("Queue has %u lines (max %u), forcing flush\n", 
                    (unsigned)queueLines, (unsigned)MAX_LOG_QUEUE_LINES);
      flushQueueBatch(100); // flush more aggressively
    } else {
      flushQueueBatch(50);
    }
    g_lastFlush = now;
  }
}

// ---------------- Relay + heartbeat + RFID helpers
// [DEPRECATED] Old blocking relay function - replaced with non-blocking version
// Kept for compatibility but should not be used
void relayGrantPulse() {
  LOG_WARN("relayGrantPulse() is deprecated, use relayStartPulse().");
  relayStartPulse();
}

void heartbeat(bool immediate=false) {
  if (g_firestoreBusy) return;
  if (!immediate && millis() - g_lastHeartbeat < HEARTBEAT_MS) return;
  if (WiFi.status() == WL_CONNECTED && app.ready()) {
    (void)fsRegisterOrUpdateDevice();  // upsert both device docs
    bool act=false;
    if (fsFetchActivityStatus(act)) {
      g_activityEnabled = act;
      writeActivityCache(act);
      applyActivityRelay();
      LOG_DEBUG("Heartbeat device_status=%s", g_activityEnabled ? "ON" : "OFF");
    }
    // Fallback for deployments where RTDB stream is unavailable/misconfigured.
    if (!g_activeCardsStreamHealthy) {
      fetchActiveCardsFromFirebase_v2();
    }
  }
  g_lastHeartbeat = millis();
}

// Read UID with per-reader debouncing.
// Returns empty string if no new card or if the same card was scanned too recently.
String readUidHexFrom(MFRC522 &r, RfidReaderState &state) {
  unsigned long now = millis();
  
  if (!r.PICC_IsNewCardPresent()) {
    // No card present - clear state if card was previously present
    if (state.cardPresent) {
      state.cardPresent = false;
      state.lastUid = "";
    }
    return "";
  }
  
  if (!r.PICC_ReadCardSerial()) return "";
  
  // Build normalized hex UID.
  char buf[8];
  String out;
  out.reserve(r.uid.size * 2); // pre-allocate to avoid fragmentation
  
  for (byte i = 0; i < r.uid.size && i < UID_MAX_BYTES; i++) {
    sprintf(buf, "%02X", r.uid.uidByte[i]);
    out += buf;
  }
  r.PICC_HaltA();
  
  String normalized = normalizeUidStr(out);
  
  // Debounce repeated reads while card remains near the antenna.
  if (normalized == state.lastUid && (now - state.lastScanMs) < RFID_DEBOUNCE_MS) {
    return ""; // same card, too soon
  }
  
  // Update state
  state.lastUid = normalized;
  state.lastScanMs = now;
  state.cardPresent = true;
  
  return normalized;
}

bool isUidActive(const String &uidNorm, size_t *setSizeOut) {
  bool active = false;
  size_t setSize = 0;

  if (uidNorm.length() == 0) {
    if (setSizeOut) *setSizeOut = 0;
    return false;
  }

  if (xSemaphoreTake(g_activeCardsMutex, portMAX_DELAY) == pdTRUE) {
    std::string uidKey(uidNorm.c_str());
    active = (g_activeSet.find(uidKey) != g_activeSet.end());
    setSize = g_activeSet.size();
    xSemaphoreGive(g_activeCardsMutex);
  }

  if (setSizeOut) *setSizeOut = setSize;
  return active;
}

void processRfidScan(MFRC522 &reader,
                     RfidReaderState &state,
                     const char *logType,
                     const char *logLabel,
                     bool enforceEntryRules) {
  String uid = readUidHexFrom(reader, state);
  if (uid.length() == 0) return;

  String uidNorm = normalizeUidStr(uid);
  size_t activeSetSize = 0;
  bool foundInSet = isUidActive(uidNorm, &activeSetSize);

  bool grant = false;
  if (!foundInSet) {
    LOG_DEBUG("%s denied: card=%s not in active set (size=%u)",
              logLabel, uidNorm.c_str(), (unsigned)activeSetSize);
  } else if (!enforceEntryRules) {
    grant = true; // exit only needs active-card membership
  } else if (!g_activityEnabled) {
    LOG_DEBUG("ENTRY denied: device_status is OFF.");
  } else {
    String nowHM = currentHHMM();
    String shiftStart, shiftEnd;
    if (findActiveCardTimes(uidNorm, nowHM, shiftStart, shiftEnd)) {
      grant = true;
    } else {
      LOG_DEBUG("ENTRY denied: no matching shift at now=%s (card shift=%s-%s)",
                nowHM.c_str(), shiftStart.c_str(), shiftEnd.c_str());
    }
  }

  if (grant) relayStartPulse();

  queueAccessLog(g_businessId, uid, grant, logType);
  LOG_INFO("%s %s card=%s at=%s (device_status=%s)",
           logLabel,
           grant ? "granted" : "denied",
           uid.c_str(),
           rfc3339NowUTC().c_str(),
           g_activityEnabled ? "ON" : "OFF");
}

void kickWifiReconnect(bool forceBegin) {
  if (g_ssid.length() == 0 || g_pass.length() == 0) return;
  if (WiFi.status() == WL_CONNECTED) return;

  unsigned long now = millis();
  if (!forceBegin && (now - g_lastWifiReconnectAttempt) < WIFI_RECONNECT_ATTEMPT_MS) {
    return;
  }

  g_lastWifiReconnectAttempt = now;
  WiFi.mode(g_portalActive ? WIFI_AP_STA : WIFI_STA);
  WiFi.begin(g_ssid.c_str(), g_pass.c_str());
  LOG_DEBUG("Issued WiFi reconnect attempt via WiFi.begin().");
}

// ---------------- Wi-Fi + recovery logic
void WiFiEventCB(WiFiEvent_t event) {
  unsigned long now = millis();

  if (event == ARDUINO_EVENT_WIFI_STA_DISCONNECTED) {
    LOG_WARN("WiFi disconnected event received. Starting recovery timer.");
    if (g_wifiFailStart == 0) g_wifiFailStart = now;
    kickWifiReconnect(false);
  }
  else if (event == ARDUINO_EVENT_WIFI_STA_GOT_IP) {
    ensurePortalServerRunning();
    LOG_INFO("WiFi connected: IP=%s RSSI=%d", WiFi.localIP().toString().c_str(), (int)WiFi.RSSI());
    if (g_portalServerStarted) {
      LOG_INFO("Provisioning portal reachable on STA: http://%s", WiFi.localIP().toString().c_str());
    }

    // Reset the wifi lookup state now that we have an IP
    g_wifiFailStart = 0;

    // Record GOT_IP time and request init from loop after a short settle
    g_gotIpAt = millis();
    LOG_DEBUG("GOT_IP received. Scheduling Firebase init in %lums.", GOT_IP_INIT_DELAY_MS);
    g_lastWifiReconnectAttempt = 0;

    // If portal active and forced, we'll let firebaseInitPoll() / loop determine whether to close portal.
    if (g_portalActive && g_forcedProvUntil != 0) {
      LOG_DEBUG("Portal forced window active for another %lus; awaiting Firebase result.",
                (unsigned long)((g_forcedProvUntil - millis())/1000));
    }
  }
}

// returns true if connected within a short attempt (~10s)
bool wifiConnect(uint32_t timeoutMs) {
  WiFi.mode(WIFI_STA);
  WiFi.begin(g_ssid.c_str(), g_pass.c_str());

  uint32_t start = millis();
  LOG_INFO("Connecting to WiFi SSID '%s'.", g_ssid.c_str());

  while (WiFi.status() != WL_CONNECTED) {
    if (millis() - start > timeoutMs) {
      LOG_WARN("WiFi connection timeout after %lums.", (unsigned long)timeoutMs);
      return false;
    }

    delay(200);
    yield();
    esp_task_wdt_reset();
  }

  LOG_INFO("WiFi connected.");
  g_lastWifiReconnectAttempt = 0;
  return true;
}

// WiFi recovery state machine: balances station retries and provisioning AP windows.
void wifiRecoveryTick() {
  unsigned long now = millis();

  // --- If Wi-Fi is connected ---
  if (WiFi.status() == WL_CONNECTED) {
    // If WiFi recovered while Firebase isn't ready, schedule init.
    if (!app.ready() && !g_firebaseInitInProgress && g_gotIpAt == 0) {
      g_gotIpAt = now;
      LOG_DEBUG("WiFi recovered. Scheduling Firebase init.");
    }

    // If portal is active and we have a forced window that hasn't expired, keep the portal open.
    if (g_portalActive) {
      if (g_forcedProvUntil != 0 && now < g_forcedProvUntil) {
        // Rate-limit portal keep-alive logs.
        static unsigned long lastForcedLog = 0;
        if (now - lastForcedLog >= 10000) {
          LOG_DEBUG("WiFi connected but portal forced for another %lus.",
                    (unsigned long)((g_forcedProvUntil - now) / 1000));
          lastForcedLog = now;
        }
        return;
      }

      // If portal active but no forced window, check normal provisioning window
      if (g_inProvWindow) {
        if (now - g_provWindowStart >= PROVISION_WINDOW_MS) {
          LOG_INFO("Normal provisioning window expired while connected. Stopping portal.");
          stopProvisionPortal();
          g_inProvWindow = false;
          g_provWindowStart = 0;
        } else {
          // Rate-limit portal keep-alive logs.
          static unsigned long lastNormalLog = 0;
          if (now - lastNormalLog >= 10000) {
            unsigned long remain = (PROVISION_WINDOW_MS - (now - g_provWindowStart)) / 1000;
            LOG_DEBUG("Portal active (normal). Keeping AP for another %lus.", remain);
            lastNormalLog = now;
          }
          return;
        }
      } else {
        // No inProvWindow and no forced window -> safe to stop
        stopProvisionPortal();
        g_forcedProvUntil = 0;
      }
    }

    // Reset lookup/recovery timers now that we have an IP
    g_wifiFailStart = 0;
    g_provWindowStart = 0;
    g_inProvWindow = false;
    return;
  }

  // --- Not connected ---
  if (g_wifiFailStart == 0) {
    // start tracking lookup period
    g_wifiFailStart = now;
    LOG_WARN("WiFi disconnected. Starting lookup timer.");
  }

  // Ensure disconnected state never gets stuck on passive driver retries.
  kickWifiReconnect(false);

  // If currently in provisioning window (user-initiated or forced), check end conditions
  if (g_inProvWindow) {
    // If a forced-until timestamp exists, use that expiry.
    if (g_forcedProvUntil != 0) {
      if (now >= g_forcedProvUntil) {
        LOG_INFO("Forced provisioning window ended. Closing AP and resuming WiFi retries.");
        stopProvisionPortal();
        g_inProvWindow = false;
        g_provWindowStart = 0;
        g_forcedProvUntil = 0;
        // restart lookup timer so the driver gets another full lookup window
        g_wifiFailStart = now;
        kickWifiReconnect(true);
      } else {
        // still forced; keep portal
        return;
      }
    } else {
      // Normal provisioning window expiry (PROVISION_WINDOW_MS)
      if (now - g_provWindowStart >= PROVISION_WINDOW_MS) {
        LOG_INFO("Provisioning window ended. Closing AP and resuming WiFi retries.");
        stopProvisionPortal();
        g_inProvWindow = false;
        g_provWindowStart = 0;
        // restart lookup timer so the driver gets another full lookup window
        g_wifiFailStart = now;
        kickWifiReconnect(true);
      } else {
        // still in normal provisioning window
        return;
      }
    }
    return;
  }

  // Not in provisioning window: are we still within the configured "search for wifi" timeout?
  unsigned long elapsed = now - g_wifiFailStart;
  if (elapsed < WIFI_LOOKUP_TIMEOUT_MS) {
    static unsigned long lastLog = 0;
    if (now - lastLog >= WIFI_RECONNECT_LOG_INTERVAL_MS) {
      LOG_DEBUG("WiFi still disconnected (elapsed %lus). Driver retry active.", elapsed / 1000);
      lastLog = now;
    }
    return;
  }

  // Lookup timeout expired -> open provisioning portal (normal case)
  if (!g_inProvWindow) {
    LOG_WARN("WiFi recovery timed out. Starting provisioning portal.");
    // Normal start: don't mark as forced. startProvisionPortal() will set g_inProvWindow/g_provWindowStart.
    startProvisionPortal(0);
    // Reset Wi-Fi lookup timer so after the provisioning window we get a fresh lookup window
    g_wifiFailStart = now;
  }
}

// Polling-based, non-blocking runtime reset handler.
// Call runtimeResetTick() at the very start of loop(). 
void runtimeResetTick() {
  int val = digitalRead(FACTORY_RESET_PIN); // HIGH = pressed for active-HIGH module
  unsigned long now = millis();

  static unsigned long pressStart = 0;   // timestamp when press began
  static bool pressed = false;           // are we currently seeing the button pressed?

  // Press started
  if (val == HIGH && !pressed) {
    pressed = true;
    pressStart = now;
    Serial.println("Reset press detected.");
    return;
  }

  // Still being held - do nothing here (we handle action on RELEASE)
  if (val == HIGH && pressed) {
    return;
  }

  // Button released (or not pressed)
  if (val == LOW && pressed) {
    unsigned long held = (now >= pressStart) ? (now - pressStart) : 0;
    pressed = false;
    pressStart = 0;

    // Very short deliberate press: open provisioning portal without wiping config.
    if (held >= RESET_PORTAL_OPEN_MS && held < RESET_SHORT_MS) {
      Serial.printf("Reset released after %lums: opening provisioning portal (no reset).\n", held);
      startProvisionPortal(FIREBASE_PROVISION_WINDOW_MS);
      return;
    }

    // Short press: clear user/network credentials and reboot.
    if (held >= RESET_SHORT_MS && held < RESET_LONG_MS) {
      Serial.printf("Reset released after %lums: performing normal reset (clear credentials).\n", held);
      if (clearCredentialsForNormalReset()) {
        LOG_INFO("Normal reset: cleared wifi/user credentials from config.");
      } else {
        LOG_WARN("Normal reset: failed to persist cleared credentials.");
      }
      delay(100); // settle
      ESP.restart();
      return; // device will restart
    }

    // Long press: hard reset -> format SPIFFS and reboot.
    if (held >= RESET_LONG_MS) {
      Serial.printf("Reset released after %lums: performing hard reset (format SPIFFS).\n", held);
      formatSpiffsForHardReset();
      delay(150);
      Serial.println("Restarting device...");
      ESP.restart();
      g_deviceRegisteredAfterTimeSync = false;
      return;
    }

    // Held for less than portal-open threshold -> ignore (bounce)
    if (held < RESET_PORTAL_OPEN_MS) {
      Serial.printf("Reset press too short (%lums) - ignored.\n", held);
    }
  }
}

// ---------------- Setup / Loop
void setup() {
  Serial.begin(115200);
  delay(100); // let serial stabilize
  
  // Initialize Mutexes
  g_activeCardsMutex = xSemaphoreCreateMutex();
  g_fileMutex = xSemaphoreCreateMutex();
  g_logMutex = xSemaphoreCreateMutex();

  Serial.println("\n\n=== Device Boot Starting ===");

  // ================= WATCHDOG (SAFE) =================
  // Arduino-ESP32 already initializes the Task Watchdog.
  // We only attach the current task if not already attached.

  esp_err_t wdt_status = esp_task_wdt_status(NULL);

  if (wdt_status == ESP_ERR_NOT_FOUND) {
    esp_task_wdt_add(NULL);
    Serial.println("Watchdog: task subscribed");
  } else if (wdt_status == ESP_OK) {
    Serial.println("Watchdog: task already subscribed");
  } else {
    Serial.printf("Watchdog: unexpected status = %d\n", wdt_status);
  }

  // Mount SPIFFS before any file operations (factory reset checks need SPIFFS available)
  if (!fsEnsure()) {
    Serial.println("FATAL: SPIFFS mount failed. Device cannot operate.");
    Serial.println("Please check partition table and reflash if needed.");
    // Don't proceed without filesystem
    while(1) { delay(1000); }
  }
  
  feedWatchdog(); // reset watchdog after SPIFFS mount
  
  Serial.printf("Booting... heap=%u\n", (unsigned)ESP.getFreeHeap());
  Serial.printf("Flash size map: used=%u total=%u\n", (unsigned)SPIFFS.usedBytes(), (unsigned)SPIFFS.totalBytes());


  // Configure hardware pins early with validation
  pinMode(FACTORY_RESET_PIN, INPUT_PULLDOWN); // For active-HIGH touch button: ensure pin idles LOW
  if (FACTORY_RESET_FULL_PIN >= 0) pinMode(FACTORY_RESET_FULL_PIN, INPUT_PULLUP); // tech jumper defaults HIGH

  pinMode(RELAY, OUTPUT);
  digitalWrite(RELAY, RELAY_LOCKED_STATE); // start locked
  LOG_INFO("Hardware pins configured. Relay initialized to locked state.");

  // Register WiFi events once for reconnect/Firebase scheduling state updates.
  WiFi.onEvent(WiFiEventCB);
  
  feedWatchdog();

  loadConfigIfPresent();     // load saved config (if exists)
  // Always load cached active cards so scanning works even when offline.
  loadAccessListFromFile();
  logConfigSummary();

  // Initialize both RFID readers and check that each responds.
  SPI.begin();
  rfidEntry.PCD_Init(MFRC522_ENTRY_SS, MFRC522_ENTRY_RST);
  rfidExit .PCD_Init(MFRC522_EXIT_SS, MFRC522_EXIT_RST);
  
  // Verify RFID readers are responding.
  byte entryVer = rfidEntry.PCD_ReadRegister(rfidEntry.VersionReg);
  byte exitVer = rfidExit.PCD_ReadRegister(rfidExit.VersionReg);
  LOG_DEBUG("MFRC522 entry version: 0x%02X", entryVer);
  LOG_DEBUG("MFRC522 exit version: 0x%02X", exitVer);
  
  if (entryVer == 0x00 || entryVer == 0xFF) {
    LOG_WARN("Entry RFID reader is not responding correctly.");
  }
  if (exitVer == 0x00 || exitVer == 0xFF) {
    LOG_WARN("Exit RFID reader is not responding correctly.");
  }
  
  LOG_INFO("RFID readers initialized.");
  feedWatchdog();

  // unique MAC-based ID
  uint64_t efuse = ESP.getEfuseMac(); // 48-bit factory ID
  char devId[18];
  sprintf(devId, "%02X:%02X:%02X:%02X:%02X:%02X",
          (uint8_t)(efuse >> 40), (uint8_t)(efuse >> 32), (uint8_t)(efuse >> 24),
          (uint8_t)(efuse >> 16), (uint8_t)(efuse >> 8),  (uint8_t)(efuse));
  g_mac = devId;
  LOG_INFO("Device ID: %s", g_mac.c_str());
  LOG_INFO("Firmware: version=%s build=%s stored_version=%s",
           FIRMWARE_VERSION,
           FIRMWARE_BUILD_DATE,
           readFirmwareVersion().c_str());

  // === CONFIG / PROVISIONING DECISION ===
  feedWatchdog();
  
  if (!configComplete()) {
    LOG_INFO("Config incomplete. Starting provisioning portal.");
    // No valid config yet: start setup AP in normal (non-forced) mode.
    startProvisionPortal(0); // 0 = normal (non-forced) window
  } else {
    LOG_INFO("Config complete. Attempting WiFi connection.");
    if (!wifiConnect()) {
      LOG_WARN("WiFi connection failed. Starting provisioning portal for reconfiguration.");
      // Keep portal open for the forced window so an operator can update credentials.
      startProvisionPortal(FIREBASE_PROVISION_WINDOW_MS);
    } else {
      ensurePortalServerRunning();
      feedWatchdog();
      // Got IP: kick off non-blocking time sync and Firebase init.
      timeSyncStart();
      feedWatchdog();
      firebaseInitStart();

      LOG_DEBUG("WiFi.status()=%d", WiFi.status());
      if (WiFi.status() == WL_CONNECTED) {
        LOG_INFO("WiFi connected: IP=%s RSSI=%d", WiFi.localIP().toString().c_str(), (int)WiFi.RSSI());
      } else {
        LOG_WARN("WiFi credentials loaded but station is not connected.");
      }

      feedWatchdog();
      
      if (!app.ready()) {
        LOG_WARN("Firebase init failed. Opening provisioning portal.");
        // Force the portal for FIREBASE_PROVISION_WINDOW_MS so it stays open and operator can fix credentials.
        startProvisionPortal(FIREBASE_PROVISION_WINDOW_MS);
      } else {
        LOG_INFO("Firebase ready. Entering normal operation mode.");

        // Firebase ready: normal operation.
        bool regOk = fsRegisterOrUpdateDevice();
        if (!regOk) {
          LOG_WARN("Device registration failed; heartbeat will retry.");
        }

        // Load device_status flag (server first, then local cache fallback).
        bool v = false;
        if (fsFetchActivityStatus(v)) {
          g_activityEnabled = v;
          writeActivityCache(v);
        } else if (!readActivityCache()) {
          g_activityEnabled = true;
          writeActivityCache(true);
        }
        applyActivityRelay();

        // Flush any queued logs
        flushQueueBatch(100);

        g_lastHeartbeat = millis();
        g_lastFlush     = millis();

        // Fetch active cards now. Stream startup is handled after app.ready in firebaseInitPoll().
        fetchActiveCardsFromFirebase_v2();
        feedWatchdog();
        
        // Check for OTA updates on first boot.
        LOG_INFO("Checking for firmware updates.");
        checkForOtaUpdates(true);
      }
    }
  }
  
  LOG_INFO("Setup complete. Free heap=%u bytes", ESP.getFreeHeap());
}

void loop() {
  // Feed watchdog early each loop iteration.
  feedWatchdog();
  
  // Check heap health periodically.
  static unsigned long lastHeapCheck = 0;
  if (millis() - lastHeapCheck > 10000) { // every 10 seconds
    checkHeapHealth();
    lastHeapCheck = millis();
  }
  
  // runtime reset should be checked first so operator can always trigger reprovision
  runtimeResetTick();

  // Check Firebase failures -> may trigger forced AP
  checkFirebaseFailureState();

  // Wi-Fi recovery state machine (may open provisioning window)
  wifiRecoveryTick();

  // Serve provisioning endpoints continuously; AP availability is managed separately.
  portal.handleClient();
  
  // Update non-blocking relay state machine.
  relayUpdate();
  
  // Poll time-sync state machine.
  timeSyncPoll();

  // Decide whether to start Firebase init:
  {
    unsigned long now = millis();

    // Preferred: start after GOT_IP settle delay and after any backoff window.
    if (!g_firebaseInitInProgress && !app.ready() && g_gotIpAt != 0) {
      if ((now - g_gotIpAt) >= GOT_IP_INIT_DELAY_MS && now >= g_nextFirebaseInitAttempt) {
        if (WiFi.status() == WL_CONNECTED) {
          LOG_DEBUG("Starting Firebase init after GOT_IP settle.");
          firebaseInitStart();
          g_gotIpAt = 0;
        } else {
          LOG_DEBUG("GOT_IP settle reached but WiFi is disconnected; deferring Firebase init.");
          g_gotIpAt = 0;
        }
      }
    }
    else if (g_requestFirebaseInit) {
      g_requestFirebaseInit = false;
      if (!g_firebaseInitInProgress && !app.ready() && now >= g_nextFirebaseInitAttempt) {
        LOG_DEBUG("Starting Firebase init from event-request fallback.");
        firebaseInitStart();
      } else {
        LOG_DEBUG("Firebase already ready/initializing; skipping fallback start.");
      }
    }
  }

  // Non-blocking firebase poll / initialization
  firebaseInitPoll();

  // Only run Firebase SDK loops when station is connected.
  if (!g_firebaseInitInProgress && WiFi.status() == WL_CONNECTED) {
    app.loop();
    Docs.loop();
  }

  // Keep RTDB stream alive and recover from stale/no-event sessions.
  activeCardsStreamWatchdogTick();

  // Apply active-card refresh requests raised by RTDB stream events.
  activeCardsSyncTick();

  // periodic tasks (only when not in portal-only mode)
  if (WiFi.status() == WL_CONNECTED) {
    heartbeat();          // hourly device upsert + device_status refresh
    flushQueueIfNeeded(); // hourly or when SPIFFS >= threshold or queue too large
    checkForOtaUpdates(); // check for firmware updates (throttled internally)
  }
  
  // Tiny yield to let background tasks run.
  delay(1);

  // Process RFID scans for both readers using shared access-control logic.
  processRfidScan(rfidEntry, g_entryReaderState, "entry", "ENTRY", true);
  processRfidScan(rfidExit, g_exitReaderState, "exit", "EXIT", false);
}
