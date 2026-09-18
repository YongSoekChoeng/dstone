package net.dstone.boot.sample.market.item;

public abstract class Item {
	
	protected String id;
	protected String name;
	protected int price;

	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	protected String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getPrice() {
		return price;
	}
	public void setPrice(int price) {
		this.price = price;
	}
	@Override
	public String toString() {
		return "[id=" + id + ", name=" + name + ", price=" + price + "]";
	}

}
