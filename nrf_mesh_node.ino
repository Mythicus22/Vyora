#include <ArduinoJson.h>
#include <math.h>
#include <Preferences.h>
#include <SPI.h>
#include <RF24.h>

// ESP32 DevKit V1 VSPI pin mapping. This sketch uses the existing wiring.
#define PIN_SCK   18
#define PIN_MISO  19
#define PIN_MOSI  23
#define PIN_CSN   22
#define PIN_CE    16

namespace MeshConfig {
constexpr uint8_t VERSION = 1;
constexpr uint8_t TYPE_HELLO = 1;
constexpr uint8_t TYPE_PROFILE = 2;
constexpr uint8_t TYPE_DATA = 3;
constexpr uint8_t TYPE_ACK = 4;
constexpr uint8_t MAX_HOPS = 4;
constexpr uint8_t PAYLOAD_SIZE = 14;
constexpr size_t MAX_MESSAGE_SIZE = 280;
constexpr uint8_t MAX_FRAGMENTS = MAX_MESSAGE_SIZE / PAYLOAD_SIZE;
constexpr uint8_t TX_QUEUE_SIZE = 64;
constexpr uint8_t DEDUPE_SIZE = 96;
constexpr uint8_t REASSEMBLY_SLOTS = 6;
constexpr uint8_t PENDING_SLOTS = 8;
constexpr uint8_t PEER_SLOTS = 24;
constexpr uint32_t DEDUPE_TTL_MS = 5000;
constexpr uint32_t ASSEMBLY_TTL_MS = 60000;
constexpr uint32_t ACK_TIMEOUT_MS = 20000;
constexpr uint32_t HELLO_INTERVAL_MS = 10000;
constexpr uint32_t PROFILE_INTERVAL_MS = 60000;
constexpr uint32_t LOCATION_TTL_MS = 120000;
}

RF24 radio(PIN_CE, PIN_CSN);
Preferences preferences;

#pragma pack(push, 1)
struct MeshFrame {
  uint8_t version;
  uint8_t type;
  uint8_t hopLimit;
  uint8_t sourceId[4];
  uint8_t destinationId[4];
  uint8_t messageId[4];
  uint8_t fragmentIndex;
  uint8_t fragmentCount;
  uint8_t payloadLength;
  uint8_t payload[MeshConfig::PAYLOAD_SIZE];
};
#pragma pack(pop)

static_assert(sizeof(MeshFrame) == 32, "nRF24 mesh frame must be 32 bytes");

struct TxItem {
  MeshFrame frame;
  uint32_t dueAt;
  uint8_t pendingSlot;  // 0xFF for relayed frames.
};

struct SeenFrame {
  bool active;
  uint8_t type;
  uint32_t sourceId;
  uint32_t messageId;
  uint8_t fragmentIndex;
  uint8_t bestHopLimit;
  uint32_t seenAt;
};

struct Assembly {
  bool active;
  uint8_t type;
  uint32_t sourceId;
  uint32_t destinationId;
  uint32_t messageId;
  uint8_t fragmentCount;
  bool received[MeshConfig::MAX_FRAGMENTS];
  uint8_t fragmentLengths[MeshConfig::MAX_FRAGMENTS];
  uint8_t data[MeshConfig::MAX_MESSAGE_SIZE + 1];
  uint8_t hops;
  bool rpdKnown;
  bool rpd;
  uint32_t updatedAt;
};

struct CompletedMessage {
  bool active;
  uint8_t type;
  uint32_t sourceId;
  uint32_t messageId;
  uint32_t completedAt;
  uint32_t ackSentAt;
};

struct PendingMessage {
  bool active;
  uint32_t destinationId;
  uint32_t messageId;
  uint8_t framesRemaining;
  bool txFailed;
  bool waitingForAck;
  uint32_t ackDeadline;
};

struct Peer {
  bool active;
  uint32_t nodeId;
  char userId[48];
  char callsign[32];
  uint8_t hops;
  bool rpdKnown;
  bool rpd;
  bool locationAvailable;
  float latitude;
  float longitude;
  uint32_t lastSeen;
};

TxItem txQueue[MeshConfig::TX_QUEUE_SIZE];
uint8_t txCount = 0;
uint8_t nextSeenSlot = 0;
SeenFrame seenFrames[MeshConfig::DEDUPE_SIZE] = {};
Assembly assemblies[MeshConfig::REASSEMBLY_SLOTS] = {};
CompletedMessage completed[MeshConfig::DEDUPE_SIZE] = {};
PendingMessage pending[MeshConfig::PENDING_SLOTS] = {};
Peer peers[MeshConfig::PEER_SLOTS] = {};

uint32_t nodeId = 0;
uint32_t helloSequence = 1;
uint32_t profileSequence = 1;
uint32_t nextHelloAt = 0;
uint32_t nextProfileAt = 0;
bool radioReady = false;
bool rpdSupported = false;
bool localLocationEnabled = false;
float localLatitude = 0;
float localLongitude = 0;
uint32_t localLocationUpdatedAt = 0;
char localUserId[48] = "";
char localCallsign[32] = "";
char serialInput[768];
size_t serialInputLength = 0;
bool discardSerialLine = false;

void writeU32(uint8_t output[4], uint32_t value) {
  output[0] = static_cast<uint8_t>(value >> 24);
  output[1] = static_cast<uint8_t>(value >> 16);
  output[2] = static_cast<uint8_t>(value >> 8);
  output[3] = static_cast<uint8_t>(value);
}

uint32_t readU32(const uint8_t input[4]) {
  return (static_cast<uint32_t>(input[0]) << 24) |
         (static_cast<uint32_t>(input[1]) << 16) |
         (static_cast<uint32_t>(input[2]) << 8) |
         static_cast<uint32_t>(input[3]);
}

void formatId(uint32_t value, char output[9]) {
  snprintf(output, 9, "%08lX", static_cast<unsigned long>(value));
}

bool parseId(const char* text, uint32_t& value) {
  if (text == nullptr || strlen(text) != 8) {
    return false;
  }
  value = 0;
  for (uint8_t i = 0; i < 8; ++i) {
    char c = text[i];
    uint8_t digit;
    if (c >= '0' && c <= '9') digit = c - '0';
    else if (c >= 'a' && c <= 'f') digit = c - 'a' + 10;
    else if (c >= 'A' && c <= 'F') digit = c - 'A' + 10;
    else return false;
    value = (value << 4) | digit;
  }
  return true;
}

void emitStatus() {
  StaticJsonDocument<384> event;
  char idText[9];
  formatId(nodeId, idText);
  event["event"] = "status";
  event["node_id"] = idText;
  event["radio"] = radioReady ? "ready" : "error";
  event["neighbors"] = 0;
  uint8_t count = 0;
  for (const Peer& peer : peers) if (peer.active) ++count;
  event["neighbors"] = count;
  event["user_id"] = localUserId;
  event["callsign"] = localCallsign;
  bool locationFresh = localLocationEnabled && millis() - localLocationUpdatedAt <= MeshConfig::LOCATION_TTL_MS;
  event["location_shared"] = locationFresh;
  serializeJson(event, Serial);
  Serial.println();
}

void emitError(const char* code, const char* messageId = nullptr) {
  StaticJsonDocument<192> event;
  event["event"] = "error";
  event["code"] = code;
  if (messageId != nullptr) event["message_id"] = messageId;
  serializeJson(event, Serial);
  Serial.println();
}

void emitTxStatus(uint32_t messageId, const char* status) {
  StaticJsonDocument<160> event;
  char messageIdText[9];
  formatId(messageId, messageIdText);
  event["event"] = "tx_status";
  event["message_id"] = messageIdText;
  event["status"] = status;
  serializeJson(event, Serial);
  Serial.println();
}

void emitDelivered(uint32_t messageId, uint32_t destinationId) {
  StaticJsonDocument<160> event;
  char messageIdText[9];
  char destinationText[9];
  formatId(messageId, messageIdText);
  formatId(destinationId, destinationText);
  event["event"] = "delivered";
  event["message_id"] = messageIdText;
  event["to"] = destinationText;
  serializeJson(event, Serial);
  Serial.println();
}

Peer* findPeer(uint32_t id) {
  for (Peer& peer : peers) if (peer.active && peer.nodeId == id) return &peer;
  return nullptr;
}

Peer* getPeer(uint32_t id) {
  if (Peer* peer = findPeer(id)) return peer;
  for (Peer& peer : peers) {
    if (!peer.active) {
      peer = {};
      peer.active = true;
      peer.nodeId = id;
      return &peer;
    }
  }
  Peer* oldest = &peers[0];
  for (Peer& peer : peers) if (peer.lastSeen < oldest->lastSeen) oldest = &peer;
  *oldest = {};
  oldest->active = true;
  oldest->nodeId = id;
  return oldest;
}

void emitNodeSeen(uint32_t id, uint8_t hops, bool rpdKnown, bool rpd) {
  if (id == nodeId || id == 0) return;
  Peer* peer = getPeer(id);
  peer->hops = hops;
  peer->rpdKnown = rpdKnown;
  peer->rpd = rpd;
  peer->lastSeen = millis();

  StaticJsonDocument<384> event;
  char idText[9];
  formatId(id, idText);
  event["event"] = "node_seen";
  event["node_id"] = idText;
  if (peer->userId[0] != '\0') event["user_id"] = peer->userId;
  if (peer->callsign[0] != '\0') event["callsign"] = peer->callsign;
  event["hops"] = hops;
  event["last_seen_ms"] = peer->lastSeen;
  if (peer->locationAvailable) {
    event["latitude"] = peer->latitude;
    event["longitude"] = peer->longitude;
  }
  if (rpdKnown) event["rpd"] = rpd;
  else event["rpd"] = nullptr;
  serializeJson(event, Serial);
  Serial.println();
}

MeshFrame makeFrame(uint8_t type, uint32_t source, uint32_t destination,
                    uint32_t messageId, uint8_t fragmentIndex,
                    uint8_t fragmentCount, uint8_t payloadLength) {
  MeshFrame frame = {};
  frame.version = MeshConfig::VERSION;
  frame.type = type;
  frame.hopLimit = MeshConfig::MAX_HOPS;
  writeU32(frame.sourceId, source);
  writeU32(frame.destinationId, destination);
  writeU32(frame.messageId, messageId);
  frame.fragmentIndex = fragmentIndex;
  frame.fragmentCount = fragmentCount;
  frame.payloadLength = payloadLength;
  return frame;
}

bool enqueueFrame(const MeshFrame& frame, uint8_t pendingSlot = 0xFF,
                  uint16_t delayMs = 0) {
  if (txCount >= MeshConfig::TX_QUEUE_SIZE) return false;
  TxItem& item = txQueue[txCount++];
  item.frame = frame;
  item.dueAt = millis() + delayMs;
  item.pendingSlot = pendingSlot;
  return true;
}

bool seenOrImprove(const MeshFrame& frame) {
  const uint32_t source = readU32(frame.sourceId);
  const uint32_t message = readU32(frame.messageId);
  const uint32_t now = millis();
  for (SeenFrame& seen : seenFrames) {
    if (seen.active && now - seen.seenAt > MeshConfig::DEDUPE_TTL_MS) seen.active = false;
    if (seen.active && seen.type == frame.type && seen.sourceId == source &&
        seen.messageId == message && seen.fragmentIndex == frame.fragmentIndex) {
      seen.seenAt = now;
      if (frame.hopLimit <= seen.bestHopLimit) return true;
      seen.bestHopLimit = frame.hopLimit;
      return false;
    }
  }

  SeenFrame& slot = seenFrames[nextSeenSlot++ % MeshConfig::DEDUPE_SIZE];
  slot.active = true;
  slot.type = frame.type;
  slot.sourceId = source;
  slot.messageId = message;
  slot.fragmentIndex = frame.fragmentIndex;
  slot.bestHopLimit = frame.hopLimit;
  slot.seenAt = now;
  return false;
}

bool isCompleted(uint8_t type, uint32_t source, uint32_t message) {
  const uint32_t now = millis();
  for (CompletedMessage& item : completed) {
    if (item.active && now - item.completedAt > 120000) item.active = false;
    if (item.active && item.type == type && item.sourceId == source && item.messageId == message) {
      return true;
    }
  }
  return false;
}

bool shouldResendAck(uint8_t type, uint32_t source, uint32_t message) {
  uint32_t now = millis();
  for (CompletedMessage& item : completed) {
    if (item.active && item.type == type && item.sourceId == source && item.messageId == message) {
      if (now - item.ackSentAt >= 3000) {
        item.ackSentAt = now;
        return true;
      }
      return false;
    }
  }
  return false;
}

void rememberCompleted(uint8_t type, uint32_t source, uint32_t message, bool ackSent) {
  for (CompletedMessage& item : completed) {
    if (!item.active) {
      uint32_t now = millis();
      item = {true, type, source, message, now, ackSent ? now : 0};
      return;
    }
  }
  static uint8_t slot = 0;
  uint32_t now = millis();
  completed[slot++ % MeshConfig::DEDUPE_SIZE] = {true, type, source, message, now, ackSent ? now : 0};
}

Assembly* findAssembly(uint8_t type, uint32_t source, uint32_t message) {
  for (Assembly& item : assemblies) {
    if (item.active && item.type == type && item.sourceId == source && item.messageId == message) return &item;
  }
  return nullptr;
}

Assembly* createAssembly(const MeshFrame& frame, uint8_t hops, bool rpdKnown, bool rpd) {
  const uint32_t now = millis();
  for (Assembly& item : assemblies) {
    if (item.active && now - item.updatedAt > MeshConfig::ASSEMBLY_TTL_MS) item.active = false;
    if (!item.active) {
      item = {};
      item.active = true;
      item.type = frame.type;
      item.sourceId = readU32(frame.sourceId);
      item.destinationId = readU32(frame.destinationId);
      item.messageId = readU32(frame.messageId);
      item.fragmentCount = frame.fragmentCount;
      item.hops = hops;
      item.rpdKnown = rpdKnown;
      item.rpd = rpd;
      item.updatedAt = now;
      return &item;
    }
  }
  return nullptr;
}

int8_t findPending(uint32_t destination, uint32_t message) {
  for (uint8_t i = 0; i < MeshConfig::PENDING_SLOTS; ++i) {
    if (pending[i].active && pending[i].destinationId == destination && pending[i].messageId == message) return i;
  }
  return -1;
}

int8_t allocatePending(uint32_t destination, uint32_t message, uint8_t frameCount) {
  int8_t existing = findPending(destination, message);
  if (existing >= 0) {
    pending[existing].framesRemaining = frameCount;
    pending[existing].txFailed = false;
    pending[existing].waitingForAck = false;
    return existing;
  }
  for (uint8_t i = 0; i < MeshConfig::PENDING_SLOTS; ++i) {
    if (!pending[i].active) {
      pending[i] = {true, destination, message, frameCount, false, false, 0};
      return i;
    }
  }
  return -1;
}

void queueAck(uint32_t destination, uint32_t message) {
  MeshFrame ack = makeFrame(MeshConfig::TYPE_ACK, nodeId, destination, message, 0, 1, 0);
  if (!enqueueFrame(ack, 0xFF, random(5, 35))) emitError("tx_queue_full");
}

void emitMessage(uint32_t source, uint32_t destination, uint32_t message, const uint8_t* data, size_t length) {
  StaticJsonDocument<768> event;
  char sourceText[9], destinationText[9], messageText[9];
  formatId(source, sourceText);
  formatId(destination, destinationText);
  formatId(message, messageText);
  event["event"] = "message";
  event["from"] = sourceText;
  event["to"] = destinationText;
  event["message_id"] = messageText;
  event["text"] = reinterpret_cast<const char*>(data);
  serializeJson(event, Serial);
  Serial.println();
}

void processComplete(Assembly& assembly) {
  const uint8_t type = assembly.type;
  const uint32_t source = assembly.sourceId;
  const uint32_t destination = assembly.destinationId;
  const uint32_t message = assembly.messageId;
  const size_t finalLength = static_cast<size_t>(assembly.fragmentCount - 1) * MeshConfig::PAYLOAD_SIZE +
                             assembly.fragmentLengths[assembly.fragmentCount - 1];

  bool duplicate = isCompleted(type, source, message);
  bool ackSent = false;
  if (type == MeshConfig::TYPE_DATA) {
    if (!duplicate) emitMessage(source, destination, message, assembly.data, finalLength);
    if (!duplicate && destination != 0 && destination == nodeId) {
      queueAck(source, message);
      ackSent = true;
    }
  } else if (type == MeshConfig::TYPE_PROFILE) {
    if (!duplicate) {
      StaticJsonDocument<384> profile;
      DeserializationError error = deserializeJson(profile, reinterpret_cast<const char*>(assembly.data), finalLength);
      if (!error) {
        Peer* peer = getPeer(source);
        strlcpy(peer->userId, profile["user_id"] | "", sizeof(peer->userId));
        strlcpy(peer->callsign, profile["callsign"] | "", sizeof(peer->callsign));
        if (profile["latitude"].is<float>() && profile["longitude"].is<float>()) {
          float latitude = profile["latitude"].as<float>();
          float longitude = profile["longitude"].as<float>();
          peer->locationAvailable = isfinite(latitude) && isfinite(longitude) &&
                                    latitude >= -90.0f && latitude <= 90.0f &&
                                    longitude >= -180.0f && longitude <= 180.0f;
          if (peer->locationAvailable) {
            peer->latitude = latitude;
            peer->longitude = longitude;
          }
        } else {
          peer->locationAvailable = false;
        }
        emitNodeSeen(source, assembly.hops, assembly.rpdKnown, assembly.rpd);
      }
    }
  }
  rememberCompleted(type, source, message, ackSent);
  assembly.active = false;
}

void processFragment(const MeshFrame& frame, uint8_t hops, bool rpdKnown, bool rpd) {
  const uint32_t source = readU32(frame.sourceId);
  const uint32_t destination = readU32(frame.destinationId);
  const uint32_t message = readU32(frame.messageId);
  if (frame.fragmentCount == 0 || frame.fragmentCount > MeshConfig::MAX_FRAGMENTS ||
      frame.fragmentIndex >= frame.fragmentCount || frame.payloadLength > MeshConfig::PAYLOAD_SIZE ||
      (frame.fragmentIndex + 1 < frame.fragmentCount && frame.payloadLength != MeshConfig::PAYLOAD_SIZE)) return;

  if (frame.type == MeshConfig::TYPE_DATA && destination != nodeId && destination != 0) return;
  if (isCompleted(frame.type, source, message)) {
    if (frame.type == MeshConfig::TYPE_DATA && destination == nodeId &&
        shouldResendAck(frame.type, source, message)) queueAck(source, message);
    return;
  }

  Assembly* assembly = findAssembly(frame.type, source, message);
  if (assembly == nullptr) assembly = createAssembly(frame, hops, rpdKnown, rpd);
  if (assembly == nullptr || assembly->fragmentCount != frame.fragmentCount) return;

  uint8_t index = frame.fragmentIndex;
  size_t offset = static_cast<size_t>(index) * MeshConfig::PAYLOAD_SIZE;
  memcpy(assembly->data + offset, frame.payload, frame.payloadLength);
  assembly->received[index] = true;
  assembly->fragmentLengths[index] = frame.payloadLength;
  assembly->updatedAt = millis();
  assembly->hops = hops;
  assembly->rpdKnown = rpdKnown;
  assembly->rpd = rpd;

  for (uint8_t i = 0; i < assembly->fragmentCount; ++i) if (!assembly->received[i]) return;
  size_t finalOffset = static_cast<size_t>(assembly->fragmentCount - 1) * MeshConfig::PAYLOAD_SIZE;
  assembly->data[finalOffset + assembly->fragmentLengths[assembly->fragmentCount - 1]] = 0;
  processComplete(*assembly);
}

void handleFrame(const MeshFrame& frame, bool rpdKnown, bool rpd) {
  if (frame.version != MeshConfig::VERSION || frame.payloadLength > MeshConfig::PAYLOAD_SIZE) return;
  uint32_t source = readU32(frame.sourceId);
  uint32_t destination = readU32(frame.destinationId);
  uint32_t message = readU32(frame.messageId);
  if (source == nodeId || source == 0) return;
  uint8_t hops = frame.hopLimit >= 1 && frame.hopLimit <= MeshConfig::MAX_HOPS
                     ? MeshConfig::MAX_HOPS - frame.hopLimit + 1
                     : MeshConfig::MAX_HOPS;

  bool duplicate = seenOrImprove(frame);
  if (frame.type == MeshConfig::TYPE_HELLO) {
    if (!duplicate) emitNodeSeen(source, hops, rpdKnown, rpd);
  } else if (frame.type == MeshConfig::TYPE_ACK) {
    if (destination == nodeId) {
      int8_t slot = findPending(source, message);
      if (slot >= 0) {
        emitDelivered(message, source);
        pending[slot].active = false;
      }
    }
  } else if (frame.type == MeshConfig::TYPE_DATA || frame.type == MeshConfig::TYPE_PROFILE) {
    processFragment(frame, hops, rpdKnown, rpd);
  } else {
    return;
  }

  if (!duplicate && frame.hopLimit > 1) {
    MeshFrame forwarded = frame;
    --forwarded.hopLimit;
    if (!enqueueFrame(forwarded, 0xFF, random(12, 90))) emitError("tx_queue_full");
  }
}

bool sendFragmented(uint8_t type, uint32_t destination, uint32_t message,
                    const uint8_t* payload, size_t length, int8_t pendingSlot) {
  uint8_t fragments = length == 0 ? 1 : (length + MeshConfig::PAYLOAD_SIZE - 1) / MeshConfig::PAYLOAD_SIZE;
  if (fragments > MeshConfig::MAX_FRAGMENTS || txCount + fragments > MeshConfig::TX_QUEUE_SIZE) return false;

  for (uint8_t i = 0; i < fragments; ++i) {
    size_t offset = static_cast<size_t>(i) * MeshConfig::PAYLOAD_SIZE;
    uint8_t chunkLength = length > offset ? min(static_cast<size_t>(MeshConfig::PAYLOAD_SIZE), length - offset) : 0;
    MeshFrame frame = makeFrame(type, nodeId, destination, message, i, fragments, chunkLength);
    if (chunkLength > 0) memcpy(frame.payload, payload + offset, chunkLength);
    if (!enqueueFrame(frame, pendingSlot, static_cast<uint16_t>(i * 12 + random(0, 12)))) return false;
  }
  return true;
}

void announceProfile() {
  if (localUserId[0] == '\0' || localCallsign[0] == '\0' || !radioReady) return;
  bool includeLocation = localLocationEnabled &&
                         millis() - localLocationUpdatedAt <= MeshConfig::LOCATION_TTL_MS;
  if (localLocationEnabled && !includeLocation) localLocationEnabled = false;
  StaticJsonDocument<256> profile;
  profile["user_id"] = localUserId;
  profile["callsign"] = localCallsign;
  if (includeLocation) {
    profile["latitude"] = localLatitude;
    profile["longitude"] = localLongitude;
  }
  char serialized[MeshConfig::MAX_MESSAGE_SIZE];
  size_t length = serializeJson(profile, serialized, sizeof(serialized));
  if (!sendFragmented(MeshConfig::TYPE_PROFILE, 0, profileSequence++,
                      reinterpret_cast<const uint8_t*>(serialized), length, -1)) {
    emitError("tx_queue_full");
  }
}

void sendHello() {
  MeshFrame hello = makeFrame(MeshConfig::TYPE_HELLO, nodeId, 0, helloSequence++, 0, 1, 0);
  if (!enqueueFrame(hello, 0xFF, random(0, 15))) emitError("tx_queue_full");
}

void handleSend(JsonDocument& command) {
  uint32_t destination, message;
  const char* destinationText = command["to"] | "";
  const char* messageText = command["message_id"] | "";
  const char* body = command["text"] | "";
  if (!parseId(destinationText, destination) || !parseId(messageText, message)) {
    emitError("invalid_id");
    return;
  }
  size_t length = strlen(body);
  if (length > MeshConfig::MAX_MESSAGE_SIZE) {
    emitError("message_too_long", messageText);
    return;
  }
  if (!radioReady) {
    emitError("radio_unavailable", messageText);
    return;
  }
  if (destination == nodeId) {
    emitMessage(nodeId, nodeId, message, reinterpret_cast<const uint8_t*>(body), length);
    emitDelivered(message, nodeId);
    return;
  }

  uint8_t frameCount = length == 0 ? 1 : (length + MeshConfig::PAYLOAD_SIZE - 1) / MeshConfig::PAYLOAD_SIZE;
  if (destination != 0 && findPending(destination, message) < 0) {
    bool hasSlot = false;
    for (const PendingMessage& item : pending) if (!item.active) hasSlot = true;
    if (!hasSlot) {
      emitError("pending_table_full", messageText);
      return;
    }
  }
  int8_t slot = allocatePending(destination, message, frameCount);
  if (slot < 0) {
    emitError("pending_table_full", messageText);
    return;
  }
  if (txCount + frameCount > MeshConfig::TX_QUEUE_SIZE ||
      !sendFragmented(MeshConfig::TYPE_DATA, destination, message,
                      reinterpret_cast<const uint8_t*>(body), length,
                      slot < 0 ? 0xFF : static_cast<uint8_t>(slot))) {
    if (slot >= 0) pending[slot].active = false;
    emitError("tx_queue_full", messageText);
    return;
  }
  emitTxStatus(message, "queued");
}

void handleTest(JsonDocument& command) {
  const char* action = command["action"] | "";
  if (strcmp(action, "serial_loopback") != 0) {
    emitError("unsupported_test");
    return;
  }
  const char* text = command["text"] | "";
  if (strlen(text) > MeshConfig::MAX_MESSAGE_SIZE) {
    emitError("message_too_long");
    return;
  }
  StaticJsonDocument<384> event;
  event["event"] = "test_result";
  event["mode"] = "serial_simulation";
  event["status"] = "ok";
  event["text"] = text;
  serializeJson(event, Serial);
  Serial.println();
}

void handleCommand(char* line) {
  StaticJsonDocument<768> command;
  DeserializationError error = deserializeJson(command, line);
  if (error) {
    emitError("invalid_json");
    return;
  }
  const char* name = command["cmd"] | "";
  if (strcmp(name, "status") == 0) {
    emitStatus();
  } else if (strcmp(name, "send") == 0) {
    handleSend(command);
  } else if (strcmp(name, "test") == 0) {
    handleTest(command);
  } else if (strcmp(name, "set_node_id") == 0) {
    uint32_t newId;
    const char* idText = command["node_id"] | "";
    if (!parseId(idText, newId) || newId == 0) {
      emitError("invalid_node_id");
      return;
    }
    nodeId = newId;
    preferences.putUInt("node_id", nodeId);
    emitStatus();
    sendHello();
    announceProfile();
  } else if (strcmp(name, "set_profile") == 0) {
    const char* userId = command["user_id"] | "";
    const char* callsign = command["callsign"] | "";
    if (strlen(userId) == 0 || strlen(userId) >= sizeof(localUserId) ||
        strlen(callsign) == 0 || strlen(callsign) >= sizeof(localCallsign)) {
      emitError("invalid_profile");
      return;
    }
    strlcpy(localUserId, userId, sizeof(localUserId));
    strlcpy(localCallsign, callsign, sizeof(localCallsign));
    preferences.putString("user_id", localUserId);
    preferences.putString("callsign", localCallsign);
    emitStatus();
    announceProfile();
  } else if (strcmp(name, "set_location") == 0) {
    bool enabled = command["enabled"] | false;
    if (!enabled) {
      localLocationEnabled = false;
      localLatitude = 0;
      localLongitude = 0;
      localLocationUpdatedAt = 0;
      emitStatus();
      announceProfile();
      return;
    }
    if (!command["latitude"].is<float>() || !command["longitude"].is<float>()) {
      emitError("invalid_location");
      return;
    }
    float latitude = command["latitude"].as<float>();
    float longitude = command["longitude"].as<float>();
    if (!isfinite(latitude) || !isfinite(longitude) || latitude < -90.0f || latitude > 90.0f ||
        longitude < -180.0f || longitude > 180.0f) {
      emitError("invalid_location");
      return;
    }
    localLatitude = latitude;
    localLongitude = longitude;
    localLocationEnabled = true;
    localLocationUpdatedAt = millis();
    emitStatus();
    announceProfile();
  } else {
    emitError("unknown_command");
  }
}

void serviceSerial() {
  while (Serial.available() > 0) {
    char c = static_cast<char>(Serial.read());
    if (c == '\n') {
      if (discardSerialLine) {
        emitError("serial_line_too_long");
      } else if (serialInputLength > 0) {
        serialInput[serialInputLength] = '\0';
        handleCommand(serialInput);
      }
      serialInputLength = 0;
      discardSerialLine = false;
    } else if (c != '\r') {
      if (!discardSerialLine && serialInputLength < sizeof(serialInput) - 1) {
        serialInput[serialInputLength++] = c;
      } else {
        discardSerialLine = true;
      }
    }
  }
}

void serviceRadioReceive() {
  while (radioReady && radio.available()) {
    MeshFrame frame = {};
    radio.read(&frame, sizeof(frame));
    bool rpd = rpdSupported ? radio.testRPD() : false;
    handleFrame(frame, rpdSupported, rpd);
  }
}

void removeTxItem(uint8_t index) {
  for (uint8_t i = index; i + 1 < txCount; ++i) txQueue[i] = txQueue[i + 1];
  --txCount;
}

void serviceTransmit() {
  if (!radioReady || txCount == 0) return;
  uint32_t now = millis();
  int8_t selected = -1;
  for (uint8_t i = 0; i < txCount; ++i) {
    if (static_cast<int32_t>(now - txQueue[i].dueAt) >= 0) {
      selected = i;
      break;
    }
  }
  if (selected < 0) return;

  TxItem item = txQueue[selected];
  removeTxItem(selected);
  radio.stopListening();
  bool sent = radio.write(&item.frame, sizeof(item.frame));
  radio.startListening();

  if (item.pendingSlot < MeshConfig::PENDING_SLOTS) {
    PendingMessage& message = pending[item.pendingSlot];
    if (message.active && message.framesRemaining > 0) {
      if (!sent) message.txFailed = true;
      --message.framesRemaining;
      if (message.framesRemaining == 0) {
        emitTxStatus(message.messageId, message.txFailed ? "transmit_error" : "transmitted");
        if (message.destinationId == 0) {
          message.active = false;
        } else {
          message.waitingForAck = true;
          message.ackDeadline = millis() + MeshConfig::ACK_TIMEOUT_MS;
        }
      }
    }
  }
}

void servicePending() {
  uint32_t now = millis();
  for (PendingMessage& message : pending) {
    if (!message.active || !message.waitingForAck || static_cast<int32_t>(now - message.ackDeadline) < 0) continue;
    char messageIdText[9];
    formatId(message.messageId, messageIdText);
    StaticJsonDocument<160> event;
    char destinationText[9];
    formatId(message.destinationId, destinationText);
    event["event"] = "delivery_timeout";
    event["message_id"] = messageIdText;
    event["to"] = destinationText;
    serializeJson(event, Serial);
    Serial.println();
    message.active = false;
  }
}

uint32_t defaultNodeId() {
  uint64_t mac = ESP.getEfuseMac();
  uint32_t id = static_cast<uint32_t>(mac) ^ static_cast<uint32_t>(mac >> 32);
  return id == 0 ? 1 : id;
}

void setup() {
  Serial.begin(115200);
  randomSeed(esp_random());

  preferences.begin("vyora", false);
  nodeId = preferences.getUInt("node_id", defaultNodeId());
  preferences.getString("user_id", localUserId, sizeof(localUserId));
  preferences.getString("callsign", localCallsign, sizeof(localCallsign));

  SPI.begin(PIN_SCK, PIN_MISO, PIN_MOSI, -1);
  radioReady = radio.begin();
  if (radioReady) {
    const byte meshAddress[6] = {'V', 'Y', 'O', 'R', 'A', 0};
    radio.setChannel(76);
    radio.setDataRate(RF24_1MBPS);
    radio.setPALevel(RF24_PA_MIN);
    radio.setPayloadSize(sizeof(MeshFrame));
    radio.setAutoAck(false);
    radio.openWritingPipe(meshAddress);
    radio.openReadingPipe(1, meshAddress);
    radio.startListening();
    rpdSupported = radio.isPVariant();
  }

  StaticJsonDocument<256> ready;
  char idText[9];
  formatId(nodeId, idText);
  ready["event"] = "ready";
  ready["node_id"] = idText;
  ready["radio"] = radioReady ? "ready" : "error";
  ready["user_id"] = localUserId;
  ready["callsign"] = localCallsign;
  ready["rpd_supported"] = rpdSupported;
  serializeJson(ready, Serial);
  Serial.println();

  nextHelloAt = millis() + 1000;
  nextProfileAt = millis() + 3000;
}

void loop() {
  serviceSerial();
  serviceRadioReceive();
  serviceTransmit();
  servicePending();

  uint32_t now = millis();
  if (radioReady && static_cast<int32_t>(now - nextHelloAt) >= 0) {
    sendHello();
    nextHelloAt = now + MeshConfig::HELLO_INTERVAL_MS;
  }
  if (radioReady && localUserId[0] != '\0' && static_cast<int32_t>(now - nextProfileAt) >= 0) {
    announceProfile();
    nextProfileAt = now + MeshConfig::PROFILE_INTERVAL_MS;
  }
}
