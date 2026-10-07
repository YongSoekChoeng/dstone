package net.dstone.boot.sample.market.sell.actor;

import java.util.List;

import net.dstone.boot.sample.market.item.Item;

public class AppleSeller extends Seller {
		
	@Override
	public void addItemToStorage(String itemId, List<Item> itemList) {
		super.addItemToStorage(itemId, itemList);
	}

	@Override
	public boolean isAvailable(Item item, int itemCnt) {
		return super.isAvailable(item, itemCnt);
	}

	@Override
	public List<Item> sell(Item item, int itemCnt, int pay) {
		List<Item> itemList = super.sell(item, itemCnt, pay);
		return itemList;
	}
	
	public void test() {
		
	}

}
