package io.antmedia.statistic;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;

import io.antmedia.statistic.type.WebRTCAudioReceiveStats;
import io.antmedia.statistic.type.WebRTCAudioSendStats;
import io.antmedia.statistic.type.WebRTCVideoReceiveStats;
import io.antmedia.statistic.type.WebRTCVideoSendStats;

public interface IStatsCollector {
	public static final String BEAN_NAME = "statsCollector";

	/**
	 * It calculates the average CPU usage for a specified time.
	 * @return the current CPU usage
	 */
	public int getCpuLoad();

	/** 
	 * It's configurable and it's based on percentage. 
	 * Max value is 100.
	 * @return the CPU limit that server does not exceed.
	 */
	public int getCpuLimit();
	
	/**
	 * It's configurable
	 * In MB
	 * @return the free RAM size that server should have all the time
	 */
	public int getMinFreeRamSize();
	
	/**
	 * In MB
	 * @return the free RAM that server can use
	 */
	public int getFreeRam();
	
	/**
	 * Check if cpu usage and ram usage does not exceed the limit
	 * @return true if not exceeding the limit, false if exceeding limit
	 */
	public boolean enoughResource();
	
	public Producer<Long, String> getKafkaProducer();
	
	public Consumer<String, String> getKafkaConsumer();
	
}
