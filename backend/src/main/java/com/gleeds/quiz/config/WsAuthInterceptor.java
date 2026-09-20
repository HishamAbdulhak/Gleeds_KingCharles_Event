package com.gleeds.quiz.config;

import java.security.Principal;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import com.gleeds.quiz.game.PlayerRepository;

/**
 * STOMP authentication and authorisation, spec → STOMP contract.
 * <ul>
 * <li>CONNECT with {@code Authorization: Bearer <admin JWT>} → {@link Admin}; with {@code X-Session-Token: <player
 * session token>} → {@link PlayerPrincipal}; anything else is refused.</li>
 * <li>SUBSCRIBE to {@code /topic/game/{id}} and SEND to {@code /app/game/{id}/**}: that Game's Players or an Admin.</li>
 * </ul>
 * A refusal sends the client a STOMP ERROR frame (which closes the session) and drops the offending frame. Not by
 * throwing: with {@code preserveReceiveOrder} Spring's ordering decorator swallows interceptor exceptions, and the
 * client would wait forever.
 */
@Component
public class WsAuthInterceptor implements ChannelInterceptor {

	public static final String SESSION_TOKEN_HEADER = "X-Session-Token";

	public record Admin(String email) implements Principal {
		@Override
		public String getName() {
			return email;
		}
	}

	/** Name is the Player id, so {@code /user/queue/…} destinations resolve per Player. */
	public record PlayerPrincipal(UUID playerId, UUID gameId) implements Principal {
		@Override
		public String getName() {
			return playerId.toString();
		}
	}

	private static final Pattern GAME_DESTINATION = Pattern.compile("^/(?:topic|app)/game/([^/]+)(?:/.*)?$");

	private final JwtDecoder jwtDecoder;
	private final PlayerRepository players;
	private final MessageChannel clientOutboundChannel;

	/** {@code @Lazy}: the outbound channel is created by the same configuration that registers this interceptor. */
	WsAuthInterceptor(JwtDecoder jwtDecoder, PlayerRepository players,
			@Lazy @Qualifier("clientOutboundChannel") MessageChannel clientOutboundChannel) {
		this.jwtDecoder = jwtDecoder;
		this.players = players;
		this.clientOutboundChannel = clientOutboundChannel;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		var accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (accessor == null || accessor.getCommand() == null) {
			return message;
		}
		var reason = switch (accessor.getCommand()) {
			case CONNECT -> {
				var principal = authenticate(accessor);
				accessor.setUser(principal);
				yield principal == null ? "Not authenticated" : null;
			}
			case SUBSCRIBE, SEND -> authorised(accessor) ? null
					: "Not your Game: " + accessor.getCommand() + " " + accessor.getDestination();
			default -> null;
		};
		if (reason == null) {
			return message;
		}
		var error = StompHeaderAccessor.create(StompCommand.ERROR);
		error.setSessionId(accessor.getSessionId());
		error.setMessage(reason);
		error.setLeaveMutable(true);
		clientOutboundChannel.send(MessageBuilder.createMessage(new byte[0], error.getMessageHeaders()));
		return null;
	}

	private Principal authenticate(StompHeaderAccessor accessor) {
		var bearer = accessor.getFirstNativeHeader("Authorization");
		if (bearer != null && bearer.startsWith("Bearer ")) {
			try {
				var jwt = jwtDecoder.decode(bearer.substring("Bearer ".length()));
				if (SecurityConfig.ADMIN_SCOPE.equals(jwt.getClaimAsString("scope"))) {
					return new Admin(jwt.getSubject());
				}
			} catch (JwtException e) {
				// falls through to the refusal below
			}
		}
		var token = accessor.getFirstNativeHeader(SESSION_TOKEN_HEADER);
		if (token != null) {
			try {
				var player = players.findBySessionToken(UUID.fromString(token));
				if (player.isPresent()) {
					return new PlayerPrincipal(player.get().getId(), player.get().getGameId());
				}
			} catch (IllegalArgumentException e) {
				// not a UUID: refused
			}
		}
		return null;
	}

	private boolean authorised(StompHeaderAccessor accessor) {
		var destination = accessor.getDestination();
		var match = destination == null ? null : GAME_DESTINATION.matcher(destination);
		if (match == null || !match.matches()) {
			return true;   // other destinations (Host topic, leaderboard, personal queues) come with their tickets
		}
		var principal = accessor.getUser();
		if (principal instanceof Admin) {
			return true;
		}
		// text compare: a non-canonical spelling of the Player's own Game id is refused too, which is the safe direction
		return principal instanceof PlayerPrincipal p && p.gameId().toString().equals(match.group(1));
	}
}
