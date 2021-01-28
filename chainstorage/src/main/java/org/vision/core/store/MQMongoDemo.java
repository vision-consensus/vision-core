package org.vision.core.store;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.Objects;

public class MQMongoDemo {
    private static final int DEFAULT_BIND_PORT = 5556;
    private static final int DEFAULT_QUEUE_LENGTH = 1;
    private static final String topic = "account2MongoDB";
    private static ZContext context = null;
    private static ZMQ.Socket publisher = null;
    public static void main(String[] args) {
        start(DEFAULT_BIND_PORT, DEFAULT_QUEUE_LENGTH);
        sub();
        while(true){
            System.out.println("send");
            publisher.sendMore(topic);
            publisher.send("test");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public static boolean start(int bindPort, int sendQueueLength) {
        System.out.println("start");
        context = new ZContext();
        publisher = context.createSocket(SocketType.PUB);
        if (Objects.isNull(publisher)) {
            return false;
        }
        if (bindPort == 0 || bindPort < 0) {
            bindPort = DEFAULT_BIND_PORT;
        }

        if (sendQueueLength < 0) {
            sendQueueLength = DEFAULT_QUEUE_LENGTH;
        }

        context.setSndHWM(sendQueueLength);
        String bindAddress = String.format("tcp://*:%d", bindPort);
        return publisher.bind(bindAddress);
    }

    private static void sub(){
        Thread thread = new Thread(() -> {
            ZContext context = new ZContext();
            ZMQ.Socket subscriber = context.createSocket(SocketType.SUB);
            while (true) {
                System.out.println("sub");
                byte[] message = subscriber.recv();
                String triggerMsg = new String(message);
                System.out.println(triggerMsg);
            }
        });
        thread.start();
    }
}
