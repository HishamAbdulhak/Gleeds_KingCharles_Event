package com.gleeds.quiz.game;

import java.security.Principal;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import com.gleeds.quiz.config.WsAuthInterceptor.PlayerPrincipal;

/** Client → server STOMP destinations under {@code /app/game/{id}}. Access is checked by WsAuthInterceptor. */
@Controller
public class GameSocketController {

	private final GameEngine engine;

	GameSocketController(GameEngine engine) {
		this.engine = engine;
	}

	/**
	 * The Player has subscribed to the Game topic and can receive the first question. An explicit message rather than
	 * a subscribe-event hook: see docs/adr/0001-ready-message-starts-solo.md. Players only: an Admin watching the
	 * topic must not start the Game before its Player is listening.
	 */
	@MessageMapping("/game/{gameId}/ready")
	public void ready(@DestinationVariable UUID gameId, Principal principal) {
		if (principal instanceof PlayerPrincipal) {
			engine.startSolo(gameId);
		}
	}

	record AnswerRequest(int questionIndex, int option) {
	}

	/** The Player's Answer. Accepted or refused on {@code /user/queue/player}; the engine decides which. */
	@MessageMapping("/game/{gameId}/answer")
	public void answer(@DestinationVariable UUID gameId, AnswerRequest req, Principal principal) {
		if (principal instanceof PlayerPrincipal p) {
			engine.answer(gameId, p.playerId(), req.questionIndex(), req.option());
		}
	}
}
