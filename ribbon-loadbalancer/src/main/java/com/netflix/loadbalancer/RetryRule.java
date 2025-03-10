/*
 *
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.loadbalancer;

/**
 * Given that
 * {@link IRule} can be cascaded, this {@link RetryRule} class allows adding a retry logic to an existing Rule.
 *
 * @author stonse
 */
public class RetryRule extends AbstractLoadBalancerRule {
    IRule subRule = new RoundRobinRule();
    long maxRetryMillis = 500;

    public RetryRule() {
    }

    public RetryRule(IRule subRule) {
        this.subRule = (subRule != null) ? subRule : new RoundRobinRule();
    }

    public RetryRule(IRule subRule, long maxRetryMillis) {
        this.subRule = (subRule != null) ? subRule : new RoundRobinRule();
        this.maxRetryMillis = (maxRetryMillis > 0) ? maxRetryMillis : 500;
    }

    public void setRule(IRule subRule) {
        this.subRule = (subRule != null) ? subRule : new RoundRobinRule();
    }

    public IRule getRule() {
        return subRule;
    }

    public void setMaxRetryMillis(long maxRetryMillis) {
        if (maxRetryMillis > 0) {
            this.maxRetryMillis = maxRetryMillis;
        } else {
            this.maxRetryMillis = 500;
        }
    }

    public long getMaxRetryMillis() {
        return maxRetryMillis;
    }


    @Override
    public void setLoadBalancer(ILoadBalancer lb) {
        super.setLoadBalancer(lb);
        subRule.setLoadBalancer(lb);
    }

    /*
     * Loop if necessary. Note that the time CAN be exceeded depending on the
     * subRule, because we're not spawning additional threads and returning
     * early.
     */
    public Server choose(ILoadBalancer lb, Object key) {
        long requestTime = System.currentTimeMillis();
        long deadline = requestTime + maxRetryMillis;

        Server answer = null;

        answer = subRule.choose(key);

        if (((answer == null) || (!answer.isAlive()))
                && (System.currentTimeMillis() < deadline)) {

            //到了时间就会进行打断
            InterruptTask task = new InterruptTask(deadline
                    - System.currentTimeMillis());


            while (!Thread.interrupted()) {
                //如果没有被打断,就继续去选择服务
                answer = subRule.choose(key);

                //然后再次进行选择服务的判断(服务是否为null,服务是否存活,并且重试的时间是否过了)
                if (((answer == null) || (!answer.isAlive()))
                        && (System.currentTimeMillis() < deadline)) {
                    /* pause and retry hoping it's transient */
                    //如果服务还是不行,就当前线程放弃执行的机会(但是也会重新争夺机会)
                    Thread.yield();
                } else {
                    //说明服务是可行的
                    break;
                }
            }

            //将定时任务进行取消,如果还没有到时间就查询到了
            task.cancel();
        }

        if ((answer == null) || (!answer.isAlive())) {
            return null;
        } else {
            return answer;
        }
    }

    @Override
    public Server choose(Object key) {
        return choose(getLoadBalancer(), key);
    }
}
