# 0001: A Solo Game starts on an explicit `ready` message, not on subscription

Status: accepted (2026-09-20, issue #4)

## Context

After `POST /api/solo` the Player connects over STOMP and subscribes to `/topic/game/{id}`. The engine must send the first question once — and only once the Player can receive it. Two triggers were on the table: Spring's `SessionSubscribeEvent`, or a client message to `/app/game/{id}/ready`.

## Decision

The client subscribes, then sends `/app/game/{id}/ready`. The engine starts the Game (status LOBBY → QUESTION, `QUESTION_START` on the topic) on that message; a second `ready` is a no-op.

`StompEndpointRegistry.setPreserveReceiveOrder(true)` makes Spring handle one client's frames strictly in order, so the SUBSCRIBE is registered with the broker before the `ready` SEND is processed. That is what makes "subscribe, then say ready" safe; the simple broker sends no RECEIPT frames, so receipts could not be used instead.

## Consequences

- A Solo Game sits in `LOBBY` between `POST /api/solo` and `ready`. The spec's "Solo skips LOBBY" describes what the Player sees (no lobby screen); the row still needs a "created, not started" state, and `LOBBY` is it.

- `SessionSubscribeEvent` was rejected: it fires after the frame is *dispatched*, not after the broker registers the subscription, so a question published from the listener can beat the subscription and be lost.
- With ordered receive, exceptions thrown by a channel interceptor are swallowed by Spring's `OrderedMessageChannelDecorator`. `WsAuthInterceptor` therefore refuses a CONNECT / SUBSCRIBE / SEND by sending the ERROR frame itself and dropping the message.
- Reconnect (a later ticket) reuses the same message: `ready` on a Game already past LOBBY will answer with `SYNC` on the personal queue instead of restarting.
- Battle (#8) reuses it too: `ready` on a Battle in LOBBY, from a Player or the Host, answers with `LOBBY_UPDATE` on the Game topic — the lobby a just-joined phone or a refreshed Host screen would otherwise wait for the next join to see.
