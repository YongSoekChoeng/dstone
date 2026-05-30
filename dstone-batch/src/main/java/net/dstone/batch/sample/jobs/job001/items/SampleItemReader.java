package net.dstone.batch.sample.jobs.job001.items;

import java.util.LinkedList;
import java.util.Queue;

import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;

import net.dstone.batch.common.items.AbstractItemReader;

// 제네릭 T는 읽어올 데이터의 타입을 나타냅니다.
public class SampleItemReader<T> extends AbstractItemReader<T> implements ItemReader<T> {

    private Queue<T> dataQueue = null;
    
    public final Object lockObj = new Object();

    public SampleItemReader() {
        this.fillQueue();
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
	private void fillQueue() {
    	log("net.dstone.batch.sample.jobs.job001.SampleItemReader.fillQueue() has been called !!!");
    	LinkedList list = new LinkedList<String>();
    	for(int i=0; i<100; i++) {
    		list.add("QueueItem-" + (i+1));
    	}
    	this.dataQueue = list;
    	log( "this.dataQueue.size() ===>>> " + this.dataQueue.size());
    }

    @Override
    public T read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
    	log(this.getClass().getName() + ".read() has been called !!!");
    	T item = null;
    	synchronized(lockObj) {
    		item = dataQueue.poll();
        	String threadId = String.valueOf(Thread.currentThread().threadId());
        	log( "threadId["+threadId+"] " + "net.dstone.batch.sample.jobs.job001.SampleItemReader.read() has been called !!! ::: item["+item+"]" + "this.dataQueue.size() ===>>> " + this.dataQueue.size());
    	}
        return item;
    }
}
