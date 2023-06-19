package com.netflix.loadbalancer;

import com.netflix.WeightServer;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WeightRoundRobinRuleTest {
    private static final Object KEY = "key";

    private AbstractLoadBalancer loadBalancer;
    private com.netflix.WeightRoundRobinRule rule;

    @Before
    public void setUp() throws Exception {
        rule = new com.netflix.WeightRoundRobinRule();
        loadBalancer = mock(AbstractLoadBalancer.class);
        setupLoadBalancer(asList(server("A", 4), server("B", 2), server("C", 1)));
        rule.setLoadBalancer(loadBalancer);
    }


    @Test
    public void testCostsTime() throws Exception {
        long l = System.currentTimeMillis();
        int threadNum = 40;
        int chooseNum = 50;
        CountDownLatch count = new CountDownLatch(threadNum);
        for (int i = 0; i < threadNum; i++) {
            Thread thread = new Thread(() -> {
                for (int z = 0; z < chooseNum; z++) {
                    Server chosen = rule.choose(KEY);
                    assertNotNull(chosen);
                    System.out.println(chosen);
                }
                count.countDown();
            });
            thread.start();
        }
        count.await();
        System.out.println("costs time is " + (System.currentTimeMillis() - l) + "ms");
    }


    private AbstractLoadBalancer setupLoadBalancer(List<Server> servers) {
        when(loadBalancer.getReachableServers()).thenReturn(servers);
        when(loadBalancer.getAllServers()).thenReturn(servers);
        return loadBalancer;
    }


    private WeightServer server(String id, int weight) {
        WeightServer server = new WeightServer(id);
        server.setAlive(true);
        server.setWeight(weight);
        return server;
    }
}
