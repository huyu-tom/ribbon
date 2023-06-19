package com.netflix;

import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.RoundRobinRule;
import com.netflix.loadbalancer.Server;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 平滑权重算法
 */
public class SmoothWeightRoundRobinRule extends RoundRobinRule {

    /**
     * 总权重
     */
    private int totalWeight = -1;

    /**
     * 当前可选择的服务索引
     */
    private int chooseServerIndex = 0;

    /**
     * 源权重数组
     */
    private int[] originWeightArray = new int[0];

    /**
     * 当前权重数组
     */
    private int[] currentWeightArray = new int[0];

    /**
     * 用于保证多个线程进行choose的安全性
     */
    private Lock lock = new ReentrantLock();


    public SmoothWeightRoundRobinRule() {
        super();
    }

    public SmoothWeightRoundRobinRule(ILoadBalancer lb) {
        super(lb);
    }


    @Override
    public Server choose(Object key) {
        ILoadBalancer loadBalancer = getLoadBalancer();

        //所有的服务
        List<Server> allServers = loadBalancer.getAllServers();
        if (allServers.size() == 0) {
            return null;
        }

        //可用的服务
        List<Server> reachableServers = loadBalancer.getReachableServers();
        if (reachableServers.size() == 0) {
            return null;
        }

        lock.lock();
        try {
            if (totalWeight <= 0) {
                init();
            }

            if (totalWeight == 0) {
                return null;
            }

            if (originWeightArray.length != allServers.size()) {
                init();
                allServers = loadBalancer.getAllServers();
                reachableServers = loadBalancer.getReachableServers();
                if (allServers.size() == 0 || reachableServers.size() == 0) {
                    return null;
                }
                if (totalWeight <= 0 || originWeightArray.length != allServers.size()) {
                    return super.choose(key);
                }
            }

            for (int j = 0; j < reachableServers.size(); j++) {
                final Server server = allServers.get(chooseServerIndex);
                smooth();
                if (Objects.isNull(server)) {
                    //放出CPU的执行权限(但是他也会去争夺)
                    Thread.yield();
                } else if (server.isAlive()) {
                    return server;
                }
            }
            return null;
        } finally {
            lock.unlock();
        }
    }


    /**
     * 平滑算法
     */
    private void smooth() {
        int maxWeight = -1;
        int maxWeightIndex = -1;
        for (int i = 0, size = currentWeightArray.length; i < size; i++) {
            if (chooseServerIndex == i) {
                if (maxWeight < (currentWeightArray[i] = currentWeightArray[i] - totalWeight + originWeightArray[i])) {
                    maxWeightIndex = i;
                    maxWeight = currentWeightArray[i];
                }
            } else {
                if (maxWeight < (currentWeightArray[i] = currentWeightArray[i] + originWeightArray[i])) {
                    maxWeightIndex = i;
                    maxWeight = currentWeightArray[i];
                }
            }
        }
        chooseServerIndex = maxWeightIndex;
    }


    /**
     * 初始化
     */
    private void init() {
        final ILoadBalancer loadBalancer = getLoadBalancer();
        final List<Server> allServers = loadBalancer.getAllServers();
        int totalWeightTemp = 0;
        int maxWeightIndex = -1;
        int maxWeight = -1;
        int index = 0;
        final int[] sourceWeightArray = new int[allServers.size()];

        for (Server server : allServers) {
            if (!(server instanceof WeightServer)) {
                totalWeight = 0;
                return;
            }
            int weight = ((WeightServer) server).getWeight();
            if (weight > maxWeight) {
                maxWeight = weight;
                maxWeightIndex = index;
            }
            sourceWeightArray[index] = weight;
            totalWeightTemp += weight;
            index++;
        }


        chooseServerIndex = maxWeightIndex;
        totalWeight = totalWeightTemp;
        this.originWeightArray = sourceWeightArray;
        currentWeightArray = Arrays.copyOf(sourceWeightArray, sourceWeightArray.length);
    }
}
