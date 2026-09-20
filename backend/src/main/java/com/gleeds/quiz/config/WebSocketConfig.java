package com.gleeds.quiz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over a raw WebSocket at {@code /ws}. Spec → STOMP contract: app prefix {@code /app}, simple broker on
 * {@code /topic} and {@code /queue}, user prefix {@code /user}. Single instance, so the in-memory broker is enough.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final String corsOrigin;
	private final WsAuthInterceptor auth;

	WebSocketConfig(@Value("${cors.origin}") String corsOrigin, WsAuthInterceptor auth) {
		this.corsOrigin = corsOrigin;
		this.auth = auth;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").setAllowedOrigins(corsOrigin);
		// one client's frames are handled strictly in order, so a SUBSCRIBE is registered before the SEND that follows it
		// (the simple broker sends no RECEIPTs, so this is what makes "subscribe, then say ready" safe)
		registry.setPreserveReceiveOrder(true);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.setApplicationDestinationPrefixes("/app");
		registry.enableSimpleBroker("/topic", "/queue");
		registry.setUserDestinationPrefix("/user");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(auth);
	}
}
