package com.bob.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.util.Arrays;
import java.util.Properties;

/**
 * Created by wangxiang on 17/10/21.
 */
public class AutoOffsetConsumer {

    public static void main(String[] args) {

        Properties props = new Properties();
        // 设置要连接的Broker, 多个的话可使用逗号隔开
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "test");
        // 设置自动提交
        props.put("enable.auto.commit", "true");
        // 偏移量自动提交频率
        props.put("auto.commit.interval.ms", "1000");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        // 消费者非线程安全
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList("foo", "test"));
        while (true) {
            ConsumerRecords<String, String> records = consumer.poll(100);
            for (ConsumerRecord<String, String> record : records)
                System.out.printf("offset = %d, key = %s, value = %s%n", record.offset(), record.key(), record.value());
        }
    }
}