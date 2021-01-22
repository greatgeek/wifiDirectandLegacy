package com.panghui.wifidirectandlegacy.routing;

import com.alibaba.fastjson.annotation.JSONField;

public class RoutingItem {
    @JSONField(name = "SENDER")
    private String sender;

    @JSONField(name = "DESTINATION")
    private String destination; // 目的地址

    @JSONField(name = "NEXTHOP")
    private String nextHop; // 下一跳地址

    @JSONField(name = "PRIOR")
    private int prior; // 优先级

    @JSONField(name = "COST")
    private int cost; // 路由开销

    @JSONField(name = "TTL")
    private int TTL; // 存活时间

    @JSONField(name = "DATA")
    private String data; // 数据

    public RoutingItem(){

    }

    public RoutingItem(String sender, String destination, String nextHop, int prior, int cost, int TTL, String data) {
        super();
        this.sender = sender;
        this.destination = destination;
        this.nextHop = nextHop;
        this.prior = prior;
        this.cost = cost;
        this.TTL = TTL;
        this.data = data;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getNextHop() {
        return nextHop;
    }

    public void setNextHop(String nextHop) {
        this.nextHop = nextHop;
    }

    public int getPrior() {
        return prior;
    }

    public void setPrior(int prior) {
        this.prior = prior;
    }

    public int getCost() {
        return cost;
    }

    public void setCost(int cost) {
        this.cost = cost;
    }

    public int getTTL() {
        return TTL;
    }

    public void setTTL(int TTL) {
        this.TTL = TTL;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }
}
