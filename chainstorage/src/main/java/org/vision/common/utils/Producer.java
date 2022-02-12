package org.vision.common.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.vision.common.parameter.CommonParameter;

import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Slf4j
public class Producer {
    private final KafkaProducer<String, String> producer;
    private static final String PREFIX = "VSCAN-";
    public static Producer instance;
    public static Producer getInstance() {
        if (Objects.isNull(instance)) {
            synchronized (Producer.class) {
                if (Objects.isNull(instance)) {
                    instance = new Producer();
                }
            }
        }
        return instance;
    }

    private Producer() {
        logger.info("kafka bootstrap servers:"+CommonParameter.PARAMETER.getKafkaBootStrapServers());
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, CommonParameter.PARAMETER.getKafkaBootStrapServers());
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer = new KafkaProducer<>(props);
    }

    public void send(String topic, String message){
        try {
            topic = PREFIX + topic;
            producer.send(new ProducerRecord<>(topic, UUID.randomUUID().toString(), message), new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    if (exception != null) {
                        exception.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * create a special partition TOPIC to send to kafka
     * @param topic -
     * @param partition - The partition to which the record should be sent
     * @param message -
     */
    public void send(String topic, Integer partition, String message){
        try {
            topic = PREFIX + topic;
            producer.send(new ProducerRecord<>(topic, partition, UUID.randomUUID().toString(), message), new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    if (exception != null) {
                        exception.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * create a special key TOPIC to send to kafka
     * @param topic -
     * @param key - The key that will be included in the record
     * @param message-
     */
    public void send(String topic, String key, String message){
        try {
            if (key == null || key.isEmpty()){
                key = UUID.randomUUID().toString();
            }
            topic = PREFIX + topic;
            producer.send(new ProducerRecord<>(topic, key, message), new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    if (exception != null) {
                        exception.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * create a special key TOPIC to send to kafka
     * @param topic -
     * @param partition - The partition to which the record should be sent
     * @param key - The key that will be included in the record
     * @param message-
     */
    public void send(String topic, Integer partition, String key, String message){
        try {
            if (key == null || key.isEmpty()){
                key = UUID.randomUUID().toString();
            }
            topic = PREFIX + topic;
            producer.send(new ProducerRecord<>(topic, partition, key, message), new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    if (exception != null) {
                        exception.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class ProducerCallBack implements Callback {

    private final long startTime;
    private final int key;
    private final String message;

    public ProducerCallBack(long startTime, int key, String message) {
        this.startTime = startTime;
        this.key = key;
        this.message = message;
    }
    public void onCompletion(RecordMetadata metadata, Exception exception) {
        long elapsedTime = System.currentTimeMillis() - startTime;
        if (metadata != null) {
            System.out.println(
                    "message(" + key + ", " + message + ") sent to partition(" + metadata.partition() +
                            "), " +
                            "offset(" + metadata.offset() + ") in " + elapsedTime + " ms");
        } else {
            exception.printStackTrace();
        }
    }
}
