package com.panghui.wifidirectandlegacy.routing;

public class RoutingTableItem {
    private String source;
    private String neighbor;
    private int hops;

    public RoutingTableItem(String source, String neighbor, int hops) {
        this.source = source;
        this.neighbor = neighbor;
        this.hops = hops;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getNeighbor() {
        return neighbor;
    }

    public void setNeighbor(String neighbor) {
        this.neighbor = neighbor;
    }

    public int getHops() {
        return hops;
    }

    public void setHops(int hops) {
        this.hops = hops;
    }

    @Override
    public String toString() {
        return "RoutingTableItem{" +
                "source='" + source + '\'' +
                ", neighbor='" + neighbor + '\'' +
                ", hops=" + hops +
                '}';
    }
}
