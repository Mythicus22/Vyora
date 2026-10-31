# VYORA Node Protocol Proposal (v1)

This is a review draft for the Android developer and firmware. **USB serial has been confirmed as the phone-to-ESP32 transport.** The existing `nrf_pa_tx` and `nrf_pa_rx` sketches remain direct-link tests. The proposed production direction is one `nrf_mesh_node` firmware image installed on every ESP32; each board gets its own persistent node ID during setup. App user identity stays separate from radio node identity.

## Phone ↔ ESP32 Serial

Use USB serial at **115200 baud, 8N1** with one UTF-8 JSON object per line (JSONL). Each command and event ends in `\n`. The app must buffer reads and split on line endings because USB reads can split or combine JSON lines.

App commands:

```json
{"cmd":"status"}
{"cmd":"set_node_id","node_id":"12AB34CD"}
{"cmd":"send","to":"89ABCDEF","message_id":"0000002A","text":"Need help"}
```

`to` is an 8-digit hexadecimal radio node ID. Reserve `00000000` for broadcast; broadcast is for discovery/test messages and is not a delivered direct message. The ESP32 should persist a configured node ID in NVS and report its active ID in `ready`/`status` events. If not configured, it can derive a stable default from the ESP32 eFuse MAC. The app remains responsible for mapping VYORA user IDs to reachable radio node IDs.

ESP32 events:

```json
{"event":"ready","node_id":"12AB34CD","radio":"ready"}
{"event":"node_seen","node_id":"89ABCDEF","hops":1}
{"event":"tx_status","message_id":"0000002A","status":"queued"}
{"event":"message","from":"89ABCDEF","message_id":"0000002A","text":"Need help"}
{"event":"delivered","message_id":"0000002A","to":"89ABCDEF"}
{"event":"error","code":"radio_unavailable","message_id":"0000002A"}
```

`tx_status: queued` means the ESP32 accepted the command. An over-air broadcast is not proof that any node received it. `delivered` is emitted only after an end-to-end ACK comes back from the destination. Do not log message bodies in production.

## Radio Packet

nRF24L01 packets have a 32-byte limit. Use one shared radio pipe/address for mesh traffic and disable hardware auto-ACK for broadcast frames; the nRF24 hardware ACK cannot mean end-to-end delivery. The firmware protocol supplies its own ACK packets. Proposed fixed 32-byte frame:

| Field | Bytes | Meaning |
|---|---:|---|
| version | 1 | Protocol version, initially `1` |
| type | 1 | `HELLO`, `DATA`, or `ACK` |
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

- Nodes broadcast periodic HELLO frames. Relays decrement `hop_limit`, add a randomized forwarding delay, and suppress duplicate frames using `(source_id, message_id, type, fragment_index)` plus the best hop limit seen.
- DATA frames are flooded with a small fixed hop limit (proposed initial value: 4). A node reassembles DATA only when addressed to its node ID, then emits a `message` event and floods an ACK back toward the source. Relays forward DATA and ACK frames once per duplicate-cache entry.
- Radio transmission status, neighbor discovery, and recipient delivery are separate states. A missing destination ACK means delivery is unconfirmed; the app retains/retries the message using the same message ID.
- Initial v1 is **volatile flood-and-forward**, not persistent store-and-forward: nodes relay while powered and listening, but do not retain messages for nodes that appear later. Durable relay queues, route selection, encryption, and larger messages are later milestones.

## Review Decisions Before Firmware Implementation

Please review these with the Android developer before treating v1 as frozen:

1. JSONL command/event names and fields, including the app-provided message ID.
2. 32-bit radio node IDs, separately mapped to app user IDs.
3. 14-byte radio fragment payload, 280-byte message cap, and 4-hop initial limit.
4. Flooding for the first multi-hop test, with no claim of persistent store-and-forward.

Once agreed, implement this as one sketch in `nrf_mesh_node/` with a configurable/persisted node ID; no per-node firmware builds should be needed.
