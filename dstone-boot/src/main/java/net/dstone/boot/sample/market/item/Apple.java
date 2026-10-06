package net.dstone.boot.sample.market.item;

public class Apple extends Item {
	public String id = "sample.item.Apple";
	public String name = "사과";
	public String prodDate;
	public boolean isFresh;
	public String getProdDate() {
		return prodDate;
	}
	public void setProdDate(String prodDate) {
		this.prodDate = prodDate;
	}
	public boolean isFresh() {
		return isFresh;
	}
	public void setFresh(boolean isFresh) {
		this.isFresh = isFresh;
	}
	@Override
	public String toString() {
		return "[id=" + id + ", name=" + name + ", prodDate=" + prodDate + ", isFresh=" + isFresh + ", price="
				+ price + "]";
	}
}
