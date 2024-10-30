# redis-bloom-practice
布隆过滤器最佳实践

设计方案，假阳率我们一般不使用，业务中只使用布隆过滤器的100%阴，即对于 BloomFilter 判断不存在的 key ，则是 100% 不存在的。

由于布隆过滤器底层使用位数组，比我们自己写的过滤器性能要高的多，何乐而不用呢？常见有如下业务场景

- 或者在数据库之前过滤非法数据
- 或者在缓存之前过滤非法数据（缓存穿透解决方案）

![img.png](debug-bloom/pic/img.png)

> 缓存穿透：意味着有特殊请求或者黑客攻击，在查询一个不存在的数据，即数据不存在 Redis 也不存在于数据库。

# 自定义RedissonClient
RedissonConfig: 主要配置RedissonClient对服务器的连接信息
```java
/**
 * @Author: ly
 * @Date: 2024/1/23 20:05
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        // 可以用"rediss://"来启用SSL连接
        config.useSingleServer().setAddress("redis://192.168.18.100:6479");
        return Redisson.create(config);
    }
}

```
# 封装工具类BloomFilterUtil
BloomFilterUtil: 注入上面注册好的RedissonClient，支持自定义布隆过滤器的细节信息：
- 过滤器名字
- 预测插入数量
- 误判率
```java
/**
 * @Description:
 * @Author: lyflexi
 * @project: redis-bloom-practice
 * @Date: 2024/9/27 0:45
 */
@Component
public class BloomFilterUtil {

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 创建布隆过滤器
     * @param filterName 过滤器名称
     * @param expectedInsertions 预测插入数量
     * @param falseProbability 误判率
     * @param <T>
     * @return
     */
    public <T> RBloomFilter<T> create(String filterName, long expectedInsertions, double falseProbability) {
        RBloomFilter<T> bloomFilter = redissonClient.getBloomFilter(filterName);
        bloomFilter.tryInit(expectedInsertions, falseProbability);
        return bloomFilter;
    }

}
```
同样的，使用@Component注解将工具类BloomFilterUtil注册到spring容器

# 单元测试-统计误判次数
运行springboot单元测试程序：DebugBloomApplicationTests

```java

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
}
```
打印信息如下：

布隆过滤器中提前设置的有效元素数为：elementCount = 991。

正常来说10001-20000号元素都不存在才对，

但是使用10001-20000号元素去测试假阳数，结果是135。

这说明布隆过滤器的阳率不是100%，也就是说当布隆过滤器说有效的时候，存在一定的误判
```java
elementCount = 9918.
假阳次数 = 135.
```

# 单元测试-布隆过滤器测试

所以布隆过滤器的用途，常常利用其100%阴来做文章

所以布隆过滤器的用途，常常利用其100%阴来做文章

所以布隆过滤器的用途，常常利用其100%阴来做文章

运行springboot单元测试程序：DebugBloomApplicationTests
- 设置预期插入数量为10000L
- 设置错误比例为0.01
- 布隆过滤器命名为ipBlackList
- 向布隆过滤器中提前插入10000L个元素
- 再次使用10000L~20000号元素模拟攻击

```java
@SpringBootTest
@Slf4j
class DebugBloomApplicationTests {

    @Test
    void contextLoads() {
    }

    @Autowired
    private BloomFilterUtil bloomFilterUtil;

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

```
打印信息如下：
- 这说明布隆过滤器对于非法数据是能够100%过滤成功的！
```java
非法数据 = 10001.
非法数据 = 10002.
非法数据 = 10003.
...
非法数据 = 19999.
```

还没结束哟，下面猫哥给出rebloom部署脚本，保证容器、代码、服务一条龙！

让大家都能跑起来，贴心的照顾每位粉丝！
# rebloom部署脚本

创建宿主机目录

```shell
mkdir -p /root/datamapping/rebloom/data
mkdir -p /root/datamapping/rebloom/config
mkdir -p /root/datamapping/rebloom/logs
```

创建redis.conf

```shell
# 基本配置，注意是容器内的默认端口，不是对外暴露的端口！
port 6379
bind 0.0.0.0

# 数据持久化
dir /data
appendonly yes

# 关闭保护模式
protected-mode no

```

执行命令

```shell
docker run -p 6479:6379 \
--name redis-bloom \
-d --restart=always \
-e TZ="Asia/Shanghai" \
 -v /root/datamapping/rebloom/config/redis.conf:/usr/local/etc/redis/redis.conf \
 -v /root/datamapping/rebloom/data:/var/lib/redis \
 -v /root/datamapping/rebloom/log:/var/log/redis \
 redislabs/rebloom:2.2.2 \
 /usr/local/bin/redis-server /usr/local/etc/redis/redis.conf \
 --appendonly yes\
 --loadmodule "/usr/lib/redis/modules/redisbloom.so"

```

设置自动启动

```shell
docker update redis-bloom --restart=always

```

shell中测试布隆过滤器命令：

```shell
[root@localhost ~]# docker exec -it redis-bloom /bin/sh
# redis-cli MODULE LIST
1) 1) "name"
   2) "bf"
   3) "ver"
   4) (integer) 20202
# redis-cli
127.0.0.1:6379> BF.ADD myfilter "hello"
(integer) 1
127.0.0.1:6379> BF.EXISTS myfilter "hello"
(integer) 1
127.0.0.1:6379> BF.EXISTS myfilter "world"
(integer) 0
127.0.0.1:6379> exit
# 

```

