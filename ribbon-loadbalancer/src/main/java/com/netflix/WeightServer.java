package com.netflix;

import com.netflix.loadbalancer.Server;

public class WeightServer extends Server {
    private int weight;

    public WeightServer(String host, int port) {
        super(host, port);
    }

    public WeightServer(String scheme, String host, int port) {
        super(scheme, host, port);
    }

    public WeightServer(String id) {
        super(id);
    }

    public int getWeight() {
        return weight;
    }

    public WeightServer setWeight(int weight) {
        this.weight = weight;
        return this;
    }
}
