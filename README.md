[Kafka](http://kafka.apache.org/)是用于构建实时数据管道和流式应用程序的一个完整系统,提供了高吞吐量,具有横向伸缩(可扩展支持热扩展),容错(允许集群中节点失败),高并发(支持数千个客户端同时读写)等特性

#### 常用命令
* zookeeper-server-start.sh
> ./bin/zookeeper-server-start.sh ./config/zookeeper.properties >/dev/null 2>&1 &
* kafka-server-start.sh
> ./bin/kafka-server-start.sh ./config/server.properties -daemon
* kafka-console-consumer.sh
> (old version) ./bin/kafka-console-consumer.sh --zookeeper localhost:2181 --from-beginning --topic test1 
>
> (new version) ./bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --from-beginning --partition 0 --topic test
* kafka-console-producer.sh
> (old version) ./bin/kafka-console-producer.sh --zookeeper localhost:2181 --topic test
> 
> (new version) ./bin/kafka-console-producer.sh --broker-list localhost:9092 --topic test
* kafka-topics.sh
> ./bin/kafka-topics.sh --zookeeper localhost:2181 --create --replication-factor 1 --partitions 1 --topic test 
>
> ./bin/kafka-topics.sh --zookeeper localhost:2181 --delete --topic test_topic **需要在配置文件server.properties中将delete.topic.enable=true才能删除主题** 
>
> ./bin/kafka-topics.sh --zookeeper localhost:2181 --list

#### 开源工具
雅虎开源的[Kafka-manager](https://github.com/yahoo/kafka-manager)

#### 专业术语
* Broker 服务代理,一个Kafka节点就是一个Broker,多个组成一个Kafka集群
* Topic 特定类型的消息流,物理上不同topic的消息分开存储,逻辑上一个topic消息虽然可存于一个或多个broker上,但用户只需指定消息的topic即可,生产或消费数据时不需要也不必关心数据存于何处
* Partition Topic物理上的分组,一个Topic可分成多个Partition,每个Partition上是有序队列 
* Segment Partition物理上由多个Segment组成  
* Offset 每个Partition都由一系列有序的不可变消息组成,Partition中每个消息都有一个连续的序列号,此号就是Offset用于标识一条消息
* At Most Once 消息可能会丢, 但绝不会重复传输, 即至多一次
* At Least Once 消息绝不会丢, 但可能会重复传输, 即至少一次
* Exactly Once 每条消息肯定会被传输一次且仅传输一次 
* ISR 跟得上leader的副本列表（包含leader）

#### HA
1. replication
> 同一个Partition可能会有多个replica(对应server.properties配置中的default.replication.factor=N),引入replication后,在这些replica间选出一个leader,producer/consumer只与这个leader交互,其它replica作为follower从leader中复制数据 
2. leader failover
> 当partition对应的leader宕机时,需要从新选择一个leader,一个基本原则是新的leader必须拥有旧leader commit过的所有消息
> 1. 优先从isr列表中选出第一个作为leader副本
> 2. 如果isr列表为空,则查看该topic的unclean.leader.election.enable配置
>    * 为true则代表允许选用非isr列表的副本作为leade,那么此时就意味着数据可能丢失
>    * 为false的话，则表示不允许，直接抛出NoReplicaOnlineException异常，造成leader副本选举失败
> 3. 如果上述配置为true，则从其他副本中选出一个作为leader副本，并且isr列表只包含该leader副本
3. broker failover
    1. "brokers/ids/[brokerId]"节点注册watcher, 当broker宕机时此watcher会被触发
    2. "brokers/ids"读取可用broker,获取宕机broker上的所有partition
    3. 对每个partition
        1. 从"brokers/topics/[topic]/partitions/[partition]/state"节点读取ISR
        2. 选择新leader
        3. 将新leader, ISR等写入state
    4. 通过RPC向其它broker发送 leaderAndISRRequest 命令 

#### 发送消息
1. 写入方式 
   * Producer采用Push模式将消息发布到Broker,每条消息被append到Partition中(顺序写)
2. 消息路由
   * 根据分区算法选择一个Partition进行存储,其机制如下
        * 客户指定了Partition,则直接使用
        * 未指定,但指定了Key,对Key的Value进行hash选出一个Partition
        * Key也未指定,轮询选出一个Partition 
3. 写入流程 
   1. 从zk的"/brokers/.../state"节点找到Partition的leader
   2. 将消息发给leader
   3. leader将消息写入本地的log
   4. followers从leader处pull消息写入本地并给leader发送ACK
   5. leader收到所有ISR的replica的ACK后,增加HW(high watermark,最后commit的offset)并向producer发送ACK确认
   
4. Producer delivery guarantee
> 当producer向broker发送消息时,一旦这条消息被commit,由于replica的存在,它就不会丢.但如果producer发送数据给broker后,遇到网络问题而造成通信中断,那Producer就无法判断该条消息是否已经commit.虽然Kafka无法确定网络故障期间发生了什么,但producer可以生成一种类似于主键的东西,发生故障时幂等性的重试多次,这样就做到了Exactly once,但目前还并未实现.所以目前默认情况下一条消息从producer到broker是确保了At least once,可通过设置producer异步发送实现At most once
   
#### 存储消息
1. 存储策略
    1. 基于时间 log.retention.hours=8, kafka默认保留七天 
    2. 基于大小 log.retention.bytes=1073741824
2. Topic创建
    1. 从zk的"/brokers/topics"节点上注册watch,当topic被创建,则会得到该topic的partition/replica的分配信息 
    2. 从zk的"/brokers/ids"节点上读取所有可用的broker列表,对于每个broker
        1. 从分配给该partition的所有replica(AR)中选择一个做为leader,并将此leader设置为新的ISR
        2. 将新的ISR写入zk的"/brokers/topics/[topic]/partitions/[partition]/state"节点中
    3. 通过RPC向其它broker发送 LeaderAndISRRequest
3. Topic删除 
    1. 从zk的"/brokers/topics"节点上注册watch,当topic被创建,则会得到该topic的partition/replica的分配信息 
    2. 若delete.topic.enable=false则结束,否则注册在"/admin/delete_topics"上的watch被触发,发送 StopReplicaRequest
    
#### 消费消息
1. high-level 
2. SimpleConsumer 
> 如果你想要对partition有更多控制权, 请使用SimpleConsumer API
3. Consumer delivery guarantee
> 如果consumer调协为autocommit,那consumer一旦读取数据就立即自动commit,如果只讨论这一读取消息的过程那么kafka确保了Exactly once 
>
> 但实际上应用程序在读取数据后一般都要进一步处理,而数据处理与commit的顺序在很大程度上决定了这一个consumer delivery guarantee 
>
> 1. 读完消息先commit再处理数据
>   * 如果consumer在commit后还没来得及处理就crash了,下次开始就无法读取到刚刚已提交而未处理的消息,对应了**at most once**
>
> 2. 读完消息先处理再commit 
>   * 如果在处理完消息commit前crash了,下次开始还会处理刚刚未commit的消息,实际上已经被处理过了,这就对应了**at least once**
>
> 3. 如果一定要做到**Exactly once**,就需要协调offset和实际操作的输出  
4. Consumer re-balance 
> 当有consumer加入或退出,以及partition的改变(broker加入/退出)均会触发 
> 1. 如果consumer group中的consumer数量少于partition数量,则至少有个consumer会消费多个partition的数据
> 2. 如果consumer数量跟partition数量相同,则正好一个consumer消费一个partition的数据
> 3. 如果consumer数量大于partition数量,会有部分consumer无法消费该topic下的任何一条数据 

#### 其它 
1. 当所有consumer属于同一个group时,系统变成了队列模式;当每个consumer的group都不相同时,系统则变成了发布订阅模式  
2. 文件刷盘策略
   1. log.flush.interval.messages=10000 当producer写入10000条消息时,刷数据到磁盘 
   2. log.flush.interval.ms=1000 间隔一秒钟时间刷数据到磁盘 
   
