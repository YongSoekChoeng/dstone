package net.dstone.boot.sample.market.buy.actor;

import java.util.HashMap;
import java.util.List;

import net.dstone.boot.sample.market.buy.action.Buy;
import net.dstone.boot.sample.market.item.Item;

public abstract class Buyer implements Buy {
	
	private void debug(Object o) {
		System.out.println(o);
	}
	
	protected HashMap<String, List<Item>> itemBuyListMap = new HashMap<String, List<Item>>();
	
	protected String id;
	protected String name;
	protected int age;

	public int pocketMoney;

	public int getPocketMoney() {
		return pocketMoney;
	}

	public void setPocketMoney(int pocketMoney) {
		this.pocketMoney = pocketMoney;
	}

	public void addItemToBasket(String itemId, List<Item> itemList) {
		List<Item> itemListToBeAdded = null;
		if(itemBuyListMap.containsKey(itemId)) {
			itemListToBeAdded = itemBuyListMap.get(itemId);
			itemListToBeAdded.addAll(itemList);
		}else {
			itemListToBeAdded = itemList; 
		}
		itemBuyListMap.put(itemId, itemListToBeAdded);
	}

	@Override
	public boolean isAffordable(Item item, int itemCnt) {
		boolean isAffordable = false;
		if( item != null ) {
			if( item.getPrice() * itemCnt <= this.pocketMoney ) {
				isAffordable = true;
			}
		}
		return isAffordable;
	}
	
	@Override
	public void buy(List<Item> itemList) {
		debug( "before buy ["+itemList.size()+"] this.pocketMoney["+this.pocketMoney+"]" );
		if( itemList != null && itemList.size() > 0 ) {
			if(this.isAffordable(itemList.get(0), itemList.size())) {
				this.addItemToBasket(itemList.get(0).getId(), itemList);
				this.pocketMoney = this.pocketMoney - itemList.get(0).getPrice() * itemList.size();
			}
		}
		debug( "after buy ["+itemList.size()+"] this.pocketMoney["+this.pocketMoney+"]" );
	}

	@Override
	public String toString() {
		return "Buyer [itemBuyListMap=" + itemBuyListMap + ", id=" + id + ", name=" + name + ", age=" + age
				+ ", pocketMoney=" + pocketMoney + "]";
	}

}
