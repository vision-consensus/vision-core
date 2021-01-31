package org.vision.common.utils;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

public class Producer extends Thread {
    private final KafkaProducer<String, String> producer;
    private final String topic;
    private int numRecords;
    private final CountDownLatch latch;

    public Producer(final String topic,
                    final String transactionalId,
                    final boolean enableIdempotency,
                    final int numRecords,
                    final int transactionTimeoutMs,
                    final CountDownLatch latch) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.129.128:19092");
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "ChainstorageProducer");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        if (transactionTimeoutMs > 0) {
            props.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, transactionTimeoutMs);
        }
        if (transactionalId != null) {
            props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionalId);
        }
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, enableIdempotency);

        producer = new KafkaProducer<>(props);
        this.topic = topic;
        this.numRecords = numRecords;
        this.latch = latch;
    }

    KafkaProducer<String, String> get() {
        return producer;
    }

    @Override
    public void run() {
        String messageKey = "";
        int recordsSent = 0;
        while (recordsSent < numRecords) {
            String messageStr = "Message_" + messageKey;
            try {
                producer.send(new ProducerRecord<>(topic,
                        messageKey,
                        messageStr)).get();
                System.out.println("Sent message: (" + messageKey + ", " + messageStr + ")");
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            recordsSent += 1;
        }
        System.out.println("Producer sent " + numRecords + " records successfully");
        latch.countDown();
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
    /**
     * A callback method the user can implement to provide asynchronous handling of request completion. This method will
     * be called when the record sent to the server has been acknowledged. When exception is not null in the callback,
     * metadata will contain the special -1 value for all fields except for topicPartition, which will be valid.
     *
     * @param metadata  The metadata for the record that was sent (i.e. the partition and offset). An empty metadata
     *                  with -1 value for all fields except for topicPartition will be returned if an error occurred.
     * @param exception The exception thrown during processing of this record. Null if no error occurred.
     */
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