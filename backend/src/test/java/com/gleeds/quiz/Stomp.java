package com.gleeds.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** A STOMP client driven the way the frontend drives the server, for the boundary tests. */
final class Stomp {

	private Stomp() {
	}

	/** Records the server's ERROR frame, which is how a refused CONNECT / SUBSCRIBE / SEND shows up client-side. */
	static class ErrorFrames extends StompSessionHandlerAdapter {
		final CompletableFuture<String> error = new CompletableFuture<>();

		/** Only ERROR frames reach the session handler's handleFrame; their {@code message} header says why. */
		@Override
		public void handleFrame(StompHeaders headers, Object payload) {
			error.complete(String.valueOf(headers.getFirst("message")));
		}
	}

	static StompSession connect(int port, Map<String, String> connectHeaders, ErrorFrames handler) throws Exception {
		var stomp = new WebSocketStompClient(new StandardWebSocketClient());
		stomp.setMessageConverter(new JacksonJsonMessageConverter());
		var headers = new StompHeaders();
		connectHeaders.forEach(headers::add);
		return stomp.connectAsync("ws://localhost:" + port + "/ws", (WebSocketHttpHeaders) null, headers, handler)
				.get(5, TimeUnit.SECONDS);
	}

	/** For tests that never read the ERROR frame: a refused CONNECT still fails the {@code get} above. */
	static StompSession connect(int port, Map<String, String> connectHeaders) throws Exception {
		return connect(port, connectHeaders, new ErrorFrames());
	}

	static StompSession connectAsPlayer(int port, String sessionToken, ErrorFrames handler) throws Exception {
		return connect(port, Map.of("X-Session-Token", sessionToken), handler);
	}

	static StompSession connectAsPlayer(int port, String sessionToken) throws Exception {
		return connectAsPlayer(port, sessionToken, new ErrorFrames());
	}

	/** The Host screen's connection: the admin JWT. */
	static StompSession connectAsAdmin(int port) throws Exception {
		return connect(port, Map.of("Authorization", "Bearer " + Fixtures.adminToken(port)));
	}

	/** Subscribes to the Game topic, then says ready (docs/adr/0001). Mirrors the frontend; ordering is guaranteed server-side. */
	static BlockingQueue<Map<String, Object>> ready(StompSession session, String gameId) {
		var events = subscribe(session, "/topic/game/" + gameId);
		session.send("/app/game/" + gameId + "/ready", Map.of());
		return events;
	}

	/** The next event on {@code events}, which must be of {@code type}; returns its payload. */
	@SuppressWarnings("unchecked")
	static Map<String, Object> next(BlockingQueue<Map<String, Object>> events, String type) throws InterruptedException {
		var event = events.poll(10, TimeUnit.SECONDS);
		assertThat(event).as("expected %s", type).isNotNull().containsEntry("type", type);
		return (Map<String, Object>) event.get("payload");
	}

	/** Every {@code {type, payload}} event arriving on {@code destination}, in order. */
	@SuppressWarnings("unchecked")
	static BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
		var events = new LinkedBlockingQueue<Map<String, Object>>();
		session.subscribe(destination, new StompFrameHandler() {
			@Override
			public Type getPayloadType(StompHeaders headers) {
				return Map.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				events.add((Map<String, Object>) payload);
			}
		});
		return events;
	}
}
