package com.gleeds.quiz.game;

import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

/** Client → server STOMP destinations under {@code /app/game/{id}}. Access is checked by WsAuthInterceptor. */
@Controller
public class GameSocketController {

	private final GameEngine engine;

	GameSocketController(GameEngine engine) {
		this.engine = engine;
	}

	/**
	 * The Player has subscribed to the Game topic and can receive the first question. An explicit message rather than
	 * a subscribe-event hook: see docs/adr/0001-ready-message-starts-solo.md.
	 */
	@MessageMapping("/game/{gameId}/ready")
	public void ready(@DestinationVariable UUID gameId) {
		engine.startSolo(gameId);
	}
}
