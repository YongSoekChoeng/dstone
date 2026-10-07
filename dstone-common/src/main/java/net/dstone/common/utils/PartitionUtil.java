package net.dstone.common.utils;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
    <테스트코드>
    int tryNum = 1305;
	java.util.List<String> list = new java.util.ArrayList<String>();
	for(int i=0; i<tryNum; i++){
		list.add(String.valueOf(i));
	}
	java.util.List<java.util.List<String>> subListArr = net.dstone.common.utils.PartitionUtil.ofSize(list, 20);
	
	System.out.println("subListArr.size():" + subListArr.size());
	for(int i=0; i<subListArr.size(); i++){
		java.util.List<String> subList = subListArr.get(i);
		System.out.println("subList.size()" + subList.size());
	}
 */
public class PartitionUtil<T> extends AbstractList<List<T>> {
	
	private final List<T> list;
	private final int chunkSize;
	
	public PartitionUtil(List<T> list, int chunkSize) {
		this.list = new ArrayList<T>(list);
		this.chunkSize = chunkSize;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> PartitionUtil<T> ofSize(List<T> list, int chunkSize) {
		return new PartitionUtil(list, chunkSize);
	}

	@Override
	public List<T> get(int index) {
		int start = index * chunkSize;
		int end = Math.min(start+chunkSize, list.size());
		if(start > end) {
			throw new IndexOutOfBoundsException("Index "+index+" is out of the list range <0, "+ (size()-1) +">");
		}
		return new ArrayList<>(list.subList(start, end));
	}

	@Override
	public int size() {
		return (int)Math.ceil( (double)list.size() / (double)chunkSize );
	}
	
	
}
