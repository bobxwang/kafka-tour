package com.bob.kafka.producr;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.*;

/**
 * Created by wangxiang on 17/10/21.
 */
public class KProducer {

    public static void main(String[] args) {

        Properties properties = new Properties();
        properties.put("bootstrap.servers", "localhost:9092");
        // all 意味着领导者将等待完整的同步副本来确认记录
        properties.put("acks", "all");
        properties.put("retries", 0);
        properties.put("batch.size", 16384);
        // 减少请求数,将指示生产才在发送请求前等待此毫秒数,这样将有更多记录到达缓冲区
        properties.put("linger.ms", 1);
        properties.put("buffer.memory", 33554432);
        properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        // 生产者是线程安全的
        Producer<String, String> KProducer = new KafkaProducer<>(properties);
        for (int i = 0; i < 100; i++)
            KProducer.send(new ProducerRecord<>("test", Integer.toString(i), Integer.toString(i) + " - iii")
                    , (recordMetadata, e) -> {
                        /*callback 一般在生产者的I/O线程中执行, 所以应当相当快速, 如果含有额外长时间处理程序, 将会延迟其它的线程消息发送
                        此时建议在callback主体中使用自己的Executor来并行处理*/
                        System.out.println(recordMetadata.toString());
                        System.out.println(recordMetadata.offset());
                    }
            );
        KProducer.close();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            System.out.println(ie.getMessage());
        }

        System.exit(0);
    }
}