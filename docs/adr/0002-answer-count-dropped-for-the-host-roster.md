# 0002: The answered count rides on `HOST_STATE`, not an `ANSWER_COUNT` on the Game topic

Status: accepted (2026-09-23, issue #9)

## Context

Issue #9 asked for `ANSWER_COUNT {answered, total}` on the Game topic after every accepted Answer, and the spec's STOMP contract listed it. The same ticket also put `HOST_STATE` on the Host-only topic: the whole roster, each Player with `answered` and their Score, published on every Answer as well.

The Game topic is everyone's — every phone in the Battle is subscribed to it. Once the big screen was reading the roster, `ANSWER_COUNT` had no reader left: the big screen counted `roster.filter(p => p.answered).length` instead, and the phones it was also delivered to discarded it. Story 31 ("the Host sees how many have answered") is the Host's line, and the Host has its own topic.

## Decision

`ANSWER_COUNT` is not published, not typed and not in the wire union, in either language. The count the Host screen shows is derived from the `HOST_STATE` roster. `.scratch/quiz-app/spec.md` → STOMP contract was amended in the same commit to drop it from the Game topic's list and say why.

## Consequences

- Issue #9's third acceptance line ("`ANSWER_COUNT {answered, total}` published on the Game topic after every accepted Answer") is not implemented as written. The behaviour it exists for — story 31 — is, on the Host topic.
- Who has answered is no longer broadcast to the Players. A phone cannot count the room, which is the right default for a Battle in one room.
- The Host topic is Admin-only (`WsAuthInterceptor`, story 36), so the count now inherits that guard rather than being readable by any Player on the Game topic.
- `HOST_STATE` is published on every accepted Answer, which is the same message rate `ANSWER_COUNT` would have had; it is a roster rather than two integers, and a Battle is 2–4 Players.
- A reader of the wire contract needs both the Game topic and the Host topic to see the whole Battle. The spec line says so.
