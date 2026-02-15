package net.dstone.boot.sample.market.buy.action;

import java.util.List;

import net.dstone.boot.sample.market.item.Item;

public interface Buy {
	public void setPocketMoney(int pocketMoney);
	public boolean isAffordable(Item item, int itemCnt);
	public void buy(List<Item> itemList);
}
