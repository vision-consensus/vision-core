package org.vision.common.utils;

import com.alibaba.fastjson.JSONObject;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class Producer {
    private final KafkaProducer<String, String> producer;
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
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.1.120:19092,192.168.1.120:19093");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer = new KafkaProducer<>(props);
    }

    public void send(String topic, String message){
        try {
            producer.send(new ProducerRecord<>(topic,
                    UUID.randomUUID().toString(),
                    message)).get();
        } catch (InterruptedException | ExecutionException e) {
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