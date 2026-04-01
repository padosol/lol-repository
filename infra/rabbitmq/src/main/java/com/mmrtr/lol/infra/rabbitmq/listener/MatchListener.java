package com.mmrtr.lol.infra.rabbitmq.listener;

import com.mmrtr.lol.infra.rabbitmq.service.MatchDataProcessor;
import com.mmrtr.lol.support.aop.TraceLogging;
import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
@TraceLogging
public class MatchListener {

    private final MessageConverter messageConverter;
    private final MatchDataProcessor matchDataProcessor;

//    @RabbitListener(
//            queues = RabbitMqBinding.Queue.MATCH_ID,
//            containerFactory = "batchRabbitListenerContainerFactory",
//            ackMode = "MANUAL"
//    )
    public void receiveMessage(
            Message message,
            Channel channel
    ) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        String platformName = message.getMessageProperties().getHeader("region");
        String matchId = messageConverter.fromMessage(message).toString();

        MatchDataProcessor.Result result = matchDataProcessor.process(matchId, platformName);

        switch (result) {
            case DUPLICATE, SUCCESS -> safeAck(channel, deliveryTag);
            case FAILURE -> safeNack(channel, deliveryTag);
        }
    }

    private void safeAck(Channel channel, long deliveryTag) {
        try {
            if (channel.isOpen()) {
                channel.basicAck(deliveryTag, false);
            } else {
                log.warn("Channel is closed, cannot ack deliveryTag {}.", deliveryTag);
            }
        } catch (AlreadyClosedException e) {
            log.warn("Channel already closed during ack for deliveryTag {}: {}", deliveryTag, e.getMessage());
        } catch (IOException e) {
            log.warn("IOException during ack for deliveryTag {}: {}", deliveryTag, e.getMessage());
        }
    }

    private void safeNack(Channel channel, long deliveryTag) {
        try {
            if (channel.isOpen()) {
                channel.basicNack(deliveryTag, false, false);
            } else {
                log.warn("Channel is closed, cannot nack deliveryTag {}.", deliveryTag);
            }
        } catch (AlreadyClosedException e) {
            log.warn("Channel already closed during nack for deliveryTag {}: {}", deliveryTag, e.getMessage());
        } catch (IOException e) {
            log.warn("IOException during nack for deliveryTag {}: {}", deliveryTag, e.getMessage());
        }
    }
}
