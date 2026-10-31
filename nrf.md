# VYORA Node Protocol (v1)

The Android developer approved this protocol direction with the broadcast, profile, message-ID, and signal-metric clarifications below. **USB serial is the confirmed phone-to-ESP32 transport.** The existing `nrf_pa_tx` and `nrf_pa_rx` sketches remain direct-link tests. Every ESP32 uses the same `nrf_mesh_node` firmware image; each board has its own persistent node ID. App user identity stays separate from radio node identity.

## Phone ↔ ESP32 Serial

Use USB serial at **115200 baud, 8N1** with one UTF-8 JSON object per line (JSONL). Each command and event ends in `\n`. The app must buffer reads and split on line endings because USB reads can split or combine JSON lines.

The firmware may add optional event fields over time. Configure the Kotlin JSON parser with `ignoreUnknownKeys = true`; treat absent/`null` optional values as unknown rather than zero.

App commands:

```json
{"cmd":"status"}
{"cmd":"test","action":"serial_loopback","text":"serial parser check"}
{"cmd":"set_node_id","node_id":"12AB34CD"}
{"cmd":"set_profile","user_id":"VYO-1234","callsign":"Echo-1"}
{"cmd":"set_location","enabled":true,"latitude":37.4219983,"longitude":-122.084}
{"cmd":"set_location","enabled":false}
{"cmd":"send","to":"89ABCDEF","message_id":"0000002A","text":"Need help"}
```

`to` and `node_id` are 8-digit hexadecimal radio node IDs. Reserve `00000000` for broadcast. Broadcast messages are delivered to every receiving app as a `message` event with `to:"00000000"`; never emit a `delivered` event for a broadcast. The ESP32 persists a configured node ID and local profile in NVS; an unset node ID is derived from the ESP32 eFuse MAC. Normally, the app should use the ID returned by `status`/`ready` and send `set_profile`; `set_node_id` is an explicit administrative override. The app maps VYORA user IDs to radio node IDs using PROFILE announcements. Do not use Kotlin `String.hashCode()` as the sole identity mapping because it is only 32 bits and can collide.

On `set_profile`, store the VYORA user ID and callsign and flood a PROFILE announcement. Receiving nodes associate the profile with its source radio node and emit `node_seen` with `node_id`, `user_id`, `callsign`, `hops`, and `last_seen_ms`. Profile announcements are discovery metadata, not chat messages.

Phone GPS is optional and requires an explicit app sharing choice. The app sends `set_location` updates; the ESP32 keeps coordinates in RAM only and includes them in PROFILE announcements while they are fresh. Location expires after 120 seconds without an update. Sending `enabled:false` clears the local coordinates and advertises a profile without location. Receiving `node_seen` events include optional `latitude` and `longitude`. Apps must expire stale location markers and should use geographic distance only to rank/display candidates; GPS distance does not prove a radio link or determine the mesh route. Do not send location automatically with SOS or chat.

The app supplies `message_id` as exactly 8 hexadecimal characters representing a persistent per-sender uint32 sequence. The sender node ID plus message ID form the message key. Do not truncate a UUID to 32 bits. The app should keep a monotonic counter in its local database and reuse the same ID when retrying the same message.

`test/serial_loopback` validates JSONL command parsing and event serialization only. Its response is explicitly marked `mode:"serial_simulation"`; it does not simulate radio discovery, a neighbor, transmission, or delivery.

ESP32 events:

```json
{"event":"ready","node_id":"12AB34CD","radio":"ready","user_id":"","callsign":"","rpd_supported":true}
{"event":"status","node_id":"12AB34CD","radio":"ready","neighbors":1,"user_id":"VYO-1234","callsign":"Echo-1"}
{"event":"test_result","mode":"serial_simulation","status":"ok","text":"serial parser check"}
{"event":"node_seen","node_id":"89ABCDEF","user_id":"VYO-1234","callsign":"Echo-1","hops":1,"last_seen_ms":123456,"latitude":37.4219983,"longitude":-122.084,"rpd":true}
{"event":"tx_status","message_id":"0000002A","status":"queued"}
{"event":"tx_status","message_id":"0000002A","status":"transmitted"}
{"event":"message","from":"89ABCDEF","to":"00000000","message_id":"0000002A","text":"Need help"}
{"event":"delivered","message_id":"0000002A","to":"89ABCDEF"}
{"event":"delivery_timeout","message_id":"0000002A","to":"89ABCDEF"}
{"event":"error","code":"radio_unavailable","message_id":"0000002A"}
```

`tx_status: queued` means the ESP32 accepted the command into its transmit queue. `transmitted` means all fragments were sent by this ESP32; it does not prove a relay or recipient received them. The app may map `transmitted` to `SENT_TO_RELAY` as a best-effort radio-send state, but must not present it as confirmed delivery. `delivered` is emitted only after an end-to-end ACK comes back from the destination. `delivery_timeout` means delivery remains unconfirmed. Error codes include `radio_unavailable`, `invalid_json`, `invalid_id`, `invalid_node_id`, `invalid_profile`, `invalid_location`, `unsupported_test`, `message_too_long`, `tx_queue_full`, and `pending_table_full`. Do not emit `delivered` for broadcasts or log message bodies in production.

After `delivery_timeout`, keep the message unconfirmed and let the app retry using the same message ID. The first firmware prototype has no durable store-and-forward queue and does not automatically retry a message.

The nRF24L01+ does not provide a numeric RSSI value. Its RPD is a coarse boolean that detects channel power above about -64 dBm; include `rpd` only as `true`/`false` when supported, otherwise `null`. This is a last-hop signal hint, not a distance estimate and not the signal level from the discovered origin node. Nordic documents the RPD threshold and its variation in the [nRF24L01+ product specification](https://devzone.nordicsemi.com/cfs-file/__key/communityserver-discussions-components-files/4/nRF24L01P_5F00_PS_5F00_v1.0.pdf).

## Radio Packet

nRF24L01 packets have a 32-byte limit. Use one shared radio pipe/address for mesh traffic and disable hardware auto-ACK for broadcast frames; the nRF24 hardware ACK cannot mean end-to-end delivery. The firmware protocol supplies its own ACK packets. Proposed fixed 32-byte frame:

| Field | Bytes | Meaning |
|---|---:|---|
| version | 1 | Protocol version, initially `1` |
| type | 1 | `HELLO`, `PROFILE`, `DATA`, or `ACK` |
| hop_limit | 1 | Remaining forwarding budget |
| source_id | 4 | 32-bit radio node ID, network byte order |
| destination_id | 4 | 32-bit radio node ID; zero is broadcast |
| message_id | 4 | Sender-generated ID, unique per source |
| fragment_index | 1 | Zero-based fragment index |
| fragment_count | 1 | Total fragments; one for HELLO/ACK |
| payload_length | 1 | Number of valid payload bytes in this frame |
| payload | 14 | Fragment bytes, padded to 14 |

All multi-byte integers use network byte order. NRF hardware CRC remains enabled. A DATA message is split into 14-byte fragments and reassembled by `(source_id, message_id)`; cap a message at 280 bytes for this first version. UTF-8 text is fragmented as bytes and decoded only after reassembly.

## Mesh Behavior and Delivery

- Nodes broadcast periodic HELLO frames and PROFILE announcements. Relays decrement `hop_limit`, add a randomized forwarding delay, and suppress duplicate frames using `(source_id, message_id, type, fragment_index)` plus the best hop limit seen.
- DATA frames are flooded with a small fixed hop limit (initial value: 4). A node reassembles DATA addressed to its node ID or the broadcast ID, then emits a `message` event. Only direct messages receive a flooded end-to-end ACK. Relays forward DATA and ACK frames once per duplicate-cache entry.
- Radio transmission status, neighbor discovery, and recipient delivery are separate states. A missing destination ACK means delivery is unconfirmed; the app retains/retries the message using the same message ID.
- Initial v1 is **volatile flood-and-forward**, not persistent store-and-forward: nodes relay while powered and listening, but do not retain messages for nodes that appear later. Durable relay queues, route selection, encryption, and larger messages are later milestones.

## v1 Scope

The v1 implementation uses the JSONL command/event names above, 32-bit radio node IDs associated with VYORA profiles, persistent per-sender 8-hex-digit message IDs, 14-byte radio fragments, a 280-byte message cap, and a four-hop flood limit. It does not claim persistent store-and-forward, encryption, or route optimization.
