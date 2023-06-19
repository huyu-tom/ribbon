package com.netflix;

import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.RoundRobinRule;
import com.netflix.loadbalancer.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * 随机权重轮训算法
 */
public class WeightRoundRobinRule extends RoundRobinRule {


    private AtomicInteger nextServerCyclicCounter = new AtomicInteger(0);

    private static Logger log = LoggerFactory.getLogger(RoundRobinRule.class);

    private volatile int size = -1;

    private Random random = new Random();


    private volatile List<Integer> serverIndexList = new ArrayList<>();


    public WeightRoundRobinRule() {
        super();
    }

    public WeightRoundRobinRule(ILoadBalancer lb) {
        super(lb);
    }


    @Override
    public Server choose(Object key) {
        ILoadBalancer loadBalancer = getLoadBalancer();

        List<Server> allServers = loadBalancer.getAllServers();
        if (allServers.size() == 0) {
            return null;
        }

        List<Server> reachableServers = loadBalancer.getReachableServers();
        if (reachableServers.size() == 0) {
            return null;
        }

        if (size <= 0) {
            synchronized (this) {
                init();
                allServers = loadBalancer.getAllServers();
                reachableServers = loadBalancer.getReachableServers();
            }
        }


        if (allServers.size() == 0 || reachableServers.size() == 0) {
            return null;
        }

        if (size != allServers.size()) {
            return super.choose(key);
        }

        final List<Integer> copyServerIndexList = serverIndexList;
        for (int i = 0, size = reachableServers.size(); i < size; i++) {
            int chooseServerIndex = incrementAndGetModulo(copyServerIndexList.size());
            Integer serverIndex = copyServerIndexList.get(chooseServerIndex);
            final Server server = allServers.get(serverIndex);
            if (Objects.isNull(server)) {
                Thread.yield();
            } else if (server.isAlive()) {
                return server;
            }
        }

        return null;
    }


    private int incrementAndGetModulo(int serverTotalWeight) {
        for (; ; ) {
            int current = nextServerCyclicCounter.get();
            int next = (current + 1) % serverTotalWeight;
            if (nextServerCyclicCounter.compareAndSet(current, next))
                return next;
            Thread.yield();
        }
    }


    private void init() {
        if (size > 0) {
            return;
        }

        ILoadBalancer loadBalancer = getLoadBalancer();
        final List<Server> allServers = loadBalancer.getAllServers();
        if (allServers.size() == 0) {
            size = -1;
            return;
        }

        final List<Server> reachableServers = loadBalancer.getReachableServers();
        if (reachableServers.size() == 0) {
            size = -1;
            return;
        }

        int index = 0;
        final List<Integer> list = new ArrayList<>();
        for (Server server : allServers) {
            if (!(server instanceof WeightServer)) {
                size = -1;
                return;
            }
            final int weight = ((WeightServer) server).getWeight();
            //如果权重很大,这里会消耗大量的内存,再加上机器很多,但是真实的业务也不可能机器之间相差太多
            for (int i = 0; i < weight; i++) {
                list.add(index);
            }
            index++;
        }


        Collections.shuffle(list, random);
        serverIndexList = list;
        size = index;
    }
}
