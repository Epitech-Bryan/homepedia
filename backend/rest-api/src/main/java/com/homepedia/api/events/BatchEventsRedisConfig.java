package com.homepedia.api.events;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class BatchEventsRedisConfig {

	@Bean
	public RedisMessageListenerContainer batchEventsListenerContainer(final RedisConnectionFactory connectionFactory,
			final BatchEventPublisher publisher) {
		final var container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.addMessageListener(publisher, new PatternTopic(BatchEventPublisher.CHANNEL));
		return container;
	}
}
