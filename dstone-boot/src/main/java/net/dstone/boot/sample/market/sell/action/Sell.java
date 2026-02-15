package net.dstone.boot.sample.market.sell.action;

import java.util.List;

import net.dstone.boot.sample.market.item.Item;

public interface Sell {
	public void addItemToStorage(String itemId, List<Item> itemList);
	public boolean isAvailable(Item item, int itemCnt);
	public List<Item> sell(Item item, int itemCnt, int pay);
}
