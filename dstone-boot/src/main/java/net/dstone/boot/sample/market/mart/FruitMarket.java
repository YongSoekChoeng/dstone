package net.dstone.boot.sample.market.mart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.dstone.boot.sample.market.buy.actor.Buyer;
import net.dstone.boot.sample.market.buy.actor.HouseWife;
import net.dstone.boot.sample.market.item.Apple;
import net.dstone.boot.sample.market.item.Item;
import net.dstone.boot.sample.market.item.Peache;
import net.dstone.boot.sample.market.sell.actor.AppleSeller;
import net.dstone.boot.sample.market.sell.actor.PeachSeller;
import net.dstone.boot.sample.market.sell.actor.Seller;

public class FruitMarket implements Market {
	
	private void debug(Object o) {
		System.out.println(o);
	}

	private HashMap<String, Seller> itemSellerMap = new HashMap<String, Seller>();
	private HashMap<String, Buyer> itemBuyerMap = new HashMap<String, Buyer>();

	@Override
	public boolean trade(Buyer buyer, Seller seller, Item item,  int itemCnt, int pay) {
		boolean isTradeMade = false;
		StringBuffer buff = new StringBuffer();
		
		if( item != null ) {
			if( !seller.isAvailable(item, itemCnt) ) {
				debug(this.getClass().getName() + ".trade() 에서 예외발생. 판매자의 재고가 충분치 않습니다. seller.getItemListFromStorage.size["+seller.getItemListFromStorage(item.getId()).size()+"]");
			}
			if( !buyer.isAffordable(item, itemCnt) ) {
				debug(this.getClass().getName() + ".trade() 에서 예외발생. 구매자의 잔고가 충분치 않습니다. buyer.getPocketMoney["+buyer.getPocketMoney()+"]");
			}else {
				buff.append("").append("\n");
				buff.append("||==============================================================================||").append("\n");
				buff.append("<판매전>").append("\n");
				buff.append("* 판매자재고 ["+seller.getItemListFromStorage(item.getId()).size()+"]").append("\n");
				buff.append("* 구매자잔고 ["+buyer.getPocketMoney()+"]").append("\n");
				buff.append("<판매>").append("\n");
				buff.append("* 아이템 ID["+item.getId()+"]").append("\n");
				buff.append("* 아이템 가격["+item.getPrice()+"]").append("\n");
				buff.append("* 아이템 판매갯수["+itemCnt+"]").append("\n");
				buff.append("* 아이템 판매금액["+item.getPrice()*itemCnt+"]").append("\n");
				buff.append("* 구매 금액["+pay+"]").append("\n");
				
				List<Item> tradeItemList = seller.sell(item, itemCnt, pay);
				buyer.buy(tradeItemList);

				buff.append("<판매후>").append("\n");
				buff.append("* 판매자재고 ["+seller.getItemListFromStorage(item.getId()).size()+"]").append("\n");
				buff.append("* 구매자잔고 ["+buyer.getPocketMoney()+"]").append("\n");
				buff.append("||==============================================================================||").append("\n");
				
				debug(buff);
				
				isTradeMade = true;
			}
		}else {
			debug(this.getClass().getName() + ".trade() 에서 예외발생. 거래대상을 선택해 주세요. item["+item+"]");
		}
		return isTradeMade;
	}
	
	public Seller setSellerForTrade(String itemId, int itemPrice, int itemStorageCnt) throws Exception {
		Seller seller = null;
		Item item = null;
		if("sample.item.Apple".equals(itemId)) {
			seller = new AppleSeller();
			((AppleSeller)seller).test();
			item = new Apple();
		}else if("sample.item.Peache".equals(itemId)) {
			seller = new PeachSeller();
			item = new Peache();
		}
		List<Item> itemList = new ArrayList<Item>();
		for(int i=0; i<itemStorageCnt; i++) {
			Item itemRow = (Item)item;
			itemRow.setId(itemId);
			itemRow.setPrice(itemPrice);
			itemList.add(itemRow);
		}
		seller.addItemToStorage(itemId, itemList);
		this.itemSellerMap.put(itemId, seller);
		
		return seller;
	}

	public Buyer setBuyerForTrade(String buyerId, int pocketMoney) throws Exception {
		Buyer buyer = null;
		if("sample.buy.actor.HouseWife".equals(buyerId)) {
			buyer = new HouseWife();
		}else {
			 throw new Exception("구매자ID를 확인하세요. buyerId["+buyerId+"]");
		}
		buyer.setPocketMoney(100000);
		this.itemBuyerMap.put(buyerId, buyer);
		return buyer;
	}
	
	public void trade(String buyerId, String itemId, int itemTradeCnt) throws Exception {
		
		Seller seller = null;
		Buyer buyer = null;
		Item item = null;
		
		try {
			if( !this.itemSellerMap.containsKey(itemId) ) {
				throw new Exception("판매자 물품ID를 확인하세요. itemId["+itemId+"]");
			}else {
				seller = this.itemSellerMap.get(itemId);
			}
			if( seller.getItemListFromStorage(itemId).size() > 0 ) {
				item = seller.getItemListFromStorage(itemId).get(0);
			}
			
			if( !this.itemBuyerMap.containsKey(buyerId) ) {
				throw new Exception("구매자ID를 확인하세요. buyerId["+buyerId+"]");
			}else {
				buyer = this.itemBuyerMap.get(buyerId);
			}

			int payment = itemTradeCnt * item.getPrice();
			
			this.trade(buyer, seller, item, itemTradeCnt, payment);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
