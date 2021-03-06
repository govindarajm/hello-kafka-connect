package us.yellosoft.hellokafkaconnect;

import org.apache.kafka.connect.source.SourceTask;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.data.Schema;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

/**
 * Sends names to Kafka
 */
public final class NameTask extends SourceTask {
  private static final Logger LOG = LoggerFactory.getLogger(NameTask.class);

  private Random random;
  private String[] kafkaTopics;
  private int kafkaPartitions;
  private URI redisAddress;
  private String nameListKey;

  private Jedis jedis;

  @Override
  public void start(final Map<String, String> props) {
    random = new Random();

    kafkaTopics = props.get(Constants.CONFIG_TOPICS).split(Constants.TOPIC_DELIMITER);
    kafkaPartitions = Integer.parseInt(props.get(Constants.CONFIG_KAFKA_PARTITIONS));

    final String redisAddressString = props.get(Constants.CONFIG_REDIS_ADDRESS);

    try {
      redisAddress = new URI(redisAddressString);
    } catch (URISyntaxException e) {
      LOG.error("Error parsing URI {} {}", redisAddressString, e);
    }

    nameListKey = props.get(Constants.CONFIG_NAME_LIST_KEY);

    jedis = new Jedis(redisAddress);
    jedis.connect();
  }

  @Override
  public List<SourceRecord> poll() {
    final List<SourceRecord> records = new LinkedList<>();

    if (jedis.isConnected()) {
      try {
        final List<String> entry = jedis.blpop(Constants.REDIS_QUERY_TIMEOUT, nameListKey);

        final String name = entry.get(1);

        if (name != null) {
          final byte[] message = name.getBytes(Charset.forName("UTF-8"));

          for (String topic : kafkaTopics) {
            final SourceRecord record = new SourceRecord(null, null, topic, random.nextInt(kafkaPartitions), Schema.BYTES_SCHEMA, message);
            records.add(record);
          }
        }
      } catch (JedisConnectionException e) {
        LOG.warn("Socket closed during Redis query {}", e);
      }
    }

    return records;
  }

  @Override
  public void stop() {
    jedis.disconnect();
  }

  @Override
  public String version() {
    return Constants.VERSION;
  }
}
