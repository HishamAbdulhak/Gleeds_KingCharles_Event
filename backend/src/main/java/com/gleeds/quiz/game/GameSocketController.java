package com.gleeds.quiz.game;

import java.security.Principal;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
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
	 * The client has subscribed to the Game topic and can receive what the Game has for it. An explicit message
	 * rather than a subscribe-event hook: see docs/adr/0001-ready-message-starts-solo.md.
	 */
	@MessageMapping("/game/{gameId}/ready")
	public void ready(@DestinationVariable UUID gameId, Principal principal,
			@Header(SimpMessageHeaderAccessor.SESSION_ID_HEADER) String sessionId) {
		engine.ready(gameId, principal.getName(), sessionId, principal instanceof PlayerPrincipal p ? p.playerId() : null);
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
