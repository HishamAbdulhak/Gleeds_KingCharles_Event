package com.gleeds.quiz;

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

	static StompSession connectAsPlayer(int port, String sessionToken, ErrorFrames handler) throws Exception {
		return connect(port, Map.of("X-Session-Token", sessionToken), handler);
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
