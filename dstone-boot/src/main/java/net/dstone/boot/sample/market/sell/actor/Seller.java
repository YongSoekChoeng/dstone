package net.dstone.boot.sample.market.sell.actor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.dstone.boot.sample.market.item.Item;
import net.dstone.boot.sample.market.sell.action.Sell;

public abstract class Seller implements Sell {
	
	private void debug(Object o) {
		System.out.println(o);
	}
	
	protected HashMap<String, List<Item>> itemSellListPap = new HashMap<String, List<Item>>();
	
	protected String id;
	protected String name;
	protected int age;
	
	public List<Item> getItemListFromStorage(String itemId){
		List<Item> itemList = null;
		if( itemSellListPap.containsKey(itemId) ) {
			itemList = itemSellListPap.get(itemId);
		}else {
			itemList = new ArrayList<Item>();
		}
		return itemList;
	}
	public void addItemToStorage(String itemId, List<Item> itemList) {
		itemSellListPap.put(itemId, itemList);
	}

	public boolean isAvailable(Item item, int itemCnt) {
		boolean isAvailable = false;
		if( itemSellListPap.containsKey(item.getId()) ) {
			List<Item> itemStoreList = itemSellListPap.get(item.getId());
			if( itemStoreList.size() >= itemCnt  ) {
				isAvailable = true;
			}
		}
		return isAvailable;
	}

	public List<Item> sell(Item item, int itemCnt, int pay) {
		List<Item> itemList = new ArrayList<Item>();
		if( itemSellListPap.containsKey(item.getId()) ) {
			List<Item> itemStoreList = itemSellListPap.get(item.getId());
			if( isAvailable(item, itemCnt) && itemStoreList.size() > 0 ) {
				Item rowItem = itemStoreList.get(0);
				int price = rowItem.getPrice();
				if( itemCnt*price <= pay ) {
					for(int i=0; i<itemCnt; i++) {
						rowItem = itemStoreList.get(i);
						itemList.add(rowItem);
						itemStoreList.remove(i);
						itemCnt--;
						i--;
					}
				}
			}
			itemSellListPap.put(item.getId(), itemStoreList);
		}

		return itemList;
	}

	@Override
	public String toString() {
		return "Seller [itemSellListPap=" + itemSellListPap + ", id=" + id + ", name=" + name + ", age=" + age + "]";
	}
}
