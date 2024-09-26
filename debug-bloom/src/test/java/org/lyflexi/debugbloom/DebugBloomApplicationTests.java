package org.lyflexi.debugbloom;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.lyflexi.debugbloom.utils.BloomFilterUtil;
import org.redisson.api.RBloomFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Slf4j
class DebugBloomApplicationTests {

    @Test
    void contextLoads() {
    }

    @Autowired
    private BloomFilterUtil bloomFilterUtil;

    /**
     * 假阳次数
     */
    @Test
    public void bloomMistake() {
        // 预期插入数量
        long expectedInsertions = 10000L;
        // 错误比率
        double falseProbability = 0.01;
        RBloomFilter<Long> bloomFilter = bloomFilterUtil.create("ipBlackList", expectedInsertions, falseProbability);

        // 布隆过滤器增加元素
        for (long i = 0; i < expectedInsertions; i++) {
            bloomFilter.add(i);
        }
        long elementCount = bloomFilter.count();
        log.info("elementCount = {}.", elementCount);

        // 统计误判次数
        int count = 0;
        for (long i = expectedInsertions; i < expectedInsertions * 2; i++) {
            if (bloomFilter.contains(i)) {
                count++;
            }
        }
        log.info("假阳次数 = {}.", count);
        bloomFilter.delete();
    }

    /**
     * 过滤器
     */
    @Test
    public void bloomFilter() {
        // 预期插入数量
        long expectedInsertions = 10000L;
        // 错误比率
        double falseProbability = 0.01;
        RBloomFilter<Long> bloomFilter = bloomFilterUtil.create("ipBlackList", expectedInsertions, falseProbability);

        // 布隆过滤器增加元素
        for (long i = 0; i < expectedInsertions; i++) {
            bloomFilter.add(i);
        }
        long elementCount = bloomFilter.count();
        log.info("elementCount = {}.", elementCount);

        // 过滤非法数据

        for (long i = expectedInsertions; i < expectedInsertions * 2; i++) {
            if (!bloomFilter.contains(i)) {
                log.info("非法数据 = {}.", i);
            }
        }
        bloomFilter.delete();
    }

}
