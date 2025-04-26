# Redis 主從複製、哨兵與叢集模式
![圖片描述](https://i.imgur.com/GLSvDQu.png)

## 啟動 Docker 容器

在 `docker-compose.yml` 設定完成後，您可以使用以下命令啟動 Docker 容器：

```bash
docker-compose up -d
```

## 查詢 Redis 伺服器角色
使用 docker exec 指令，檢查每個 Redis 伺服器的角色：

```bash
$ docker exec -it redis-master redis-cli info replication | grep role
role:master

$ docker exec -it redis-slave1 redis-cli info replication | grep role
role:slave

$ docker exec -it redis-slave2 redis-cli info replication | grep role
role:slave
```
## 測試資料同步
向 Master 寫入一筆資料，並檢查是否成功同步到兩個 Slave：

```bash
$ docker exec -it redis-master redis-cli set k1 v1
OK

$ docker exec -it redis-slave1 redis-cli get k1
"v1"

$ docker exec -it redis-slave2 redis-cli get k1
"v1"
```

# Redis 哨兵模式
![圖片描述](https://i.imgur.com/6KHCYvi.png)
啟動 docker-compose，並且可以看到會輸出下面這些資訊，這些資訊代表哨兵已經開始監視 Master 而且也獲得了 Slave 的資訊。

```bash
...
redis-sentinel1 | 1:X 23 Jul 2020 08:56:03.301 # +monitor master mymaster 172.30.0.2 6379 quorum 2
redis-sentinel1 | 1:X 23 Jul 2020 08:56:03.302 * +slave slave 172.30.0.3:6379 172.30.0.3 6379 @ mymaster 172.30.0.2 6379
redis-sentinel1 | 1:X 23 Jul 2020 08:56:03.483 * +slave slave 172.30.0.4:6379 172.30.0.4 6379 @ mymaster 172.30.0.2 6379
...
redis-sentinel2 | 1:X 23 Jul 2020 08:56:03.301 # +monitor master mymaster 172.30.0.2 6379 quorum 2
redis-sentinel2 | 1:X 23 Jul 2020 08:56:03.302 * +slave slave 172.30.0.3:6379 172.30.0.3 6379 @ mymaster 172.30.0.2 6379
redis-sentinel2 | 1:X 23 Jul 2020 08:56:03.456 * +slave slave 172.30.0.4:6379 172.30.0.4 6379 @ mymaster 172.30.0.2 6379
...
redis-sentinel3 | 1:X 23 Jul 2020 08:56:03.261 # +monitor master mymaster 172.30.0.2 6379 quorum 2
redis-sentinel3 | 1:X 23 Jul 2020 08:56:03.262 * +slave slave 172.30.0.3:6379 172.30.0.3 6379 @ mymaster 172.30.0.2 6379
redis-sentinel3 | 1:X 23 Jul 2020 08:56:03.456 * +slave slave 172.30.0.4:6379 172.30.0.4 6379 @ mymaster 172.30.0.2 6379

```

也可以使用下面這個指令來查看哨兵的資訊。

```bash
$ docker exec -it redis-sentinel1 redis-cli -p 26379 info sentinel
# Sentinel
sentinel_masters:1
sentinel_tilt:0
sentinel_running_scripts:0
sentinel_scripts_queue_length:0
sentinel_simulate_failure_flags:0
master0:name=mymaster,status=ok,address=172.30.0.2:6379,slaves=2,sentinels=3

```

### 故障轉移

接著我們來嘗試一下故障轉移，先停止 redis-master 這個 Container，這時哨兵就會開始啟動故障轉移，以下是進行故障轉移時的輸出。

首先可以看到三個哨兵都認定 master 為 sdown(主觀下線)，這時 Sentinel3 便認定為 odown(客觀下線)，並打算發起投票要求成為領頭哨兵。

```bash
redis-sentinel1 | 1:X 23 Jul 2020 09:28:35.907 # +sdown master mymaster 172.30.0.2 6379
redis-sentinel2 | 1:X 23 Jul 2020 09:28:35.907 # +sdown master mymaster 172.30.0.2 6379
redis-sentinel3 | 1:X 23 Jul 2020 09:28:35.919 # +sdown master mymaster 172.30.0.2 6379
redis-sentinel3 | 1:X 23 Jul 2020 09:28:35.990 # +odown master mymaster 172.30.0.2 6379 #quorum 3/2
redis-sentinel3 | 1:X 23 Jul 2020 09:28:35.990 # +new-epoch 1
redis-sentinel3 | 1:X 23 Jul 2020 09:28:35.990 # +try-failover master mymaster 172.30.0.2 6379
```

與此同時 Sentinel2 也認定為客觀下線，雖然看起來是 Sentinel3 先認定客觀下線打算發起投票了，但是最後還是由 Sentinel2 發起投票要求成為領頭哨兵。Sentinel2 和 Sentinel3 都加入參選且各自投給自己，Sentinel1 則沒有投票所以這一輪流局。

```bash
redis-sentinel2 | 1:X 23 Jul 2020 09:28:35.990 # +odown master mymaster 172.30.0.2 6379 #quorum 2/2
redis-sentinel2 | 1:X 23 Jul 2020 09:28:35.990 # +new-epoch 1
redis-sentinel2 | 1:X 23 Jul 2020 09:28:35.990 # +try-failover master mymaster 172.30.0.2 6379
redis-sentinel2 | 1:X 23 Jul 2020 09:28:36.066 # +vote-for-leader 8ce97092515e4e1f53f5bdfa276676c664dc100b 1
redis-sentinel2 | 1:X 23 Jul 2020 09:28:36.092 # 8fc467a6fa4a1d798be4a054d78db4c5db10fb97 voted for 8fc467a6fa4a1d798be4a054d78db4c5db10fb97 1
redis-sentinel3 | 1:X 23 Jul 2020 09:28:36.092 # +vote-for-leader 8fc467a6fa4a1d798be4a054d78db4c5db10fb97 1
redis-sentinel3 | 1:X 23 Jul 2020 09:28:36.092 # 8ce97092515e4e1f53f5bdfa276676c664dc100b voted for 8ce97092515e4e1f53f5bdfa276676c664dc100b 1
```

Sentinel1 再次發起了一輪投票要推舉 Sentinel2 為領頭哨兵，Sentinel2 和 Sentinel3 都投給 Sentinel2，所以最後 Sentinel2 當選。

```bash
redis-sentinel1 | 1:X 23 Jul 2020 09:28:36.124 # +new-epoch 1
redis-sentinel1 | 1:X 23 Jul 2020 09:28:36.162 # +vote-for-leader 8ce97092515e4e1f53f5bdfa276676c664dc100b 1
redis-sentinel3 | 1:X 23 Jul 2020 09:28:36.162 # 8fc467a6fa4a1d798be4a054d78db4c5db10fb97 voted for 8ce97092515e4e1f53f5bdfa276676c664dc100b 1
redis-sentinel2 | 1:X 23 Jul 2020 09:28:36.162 # 8ce97092515e4e1f53f5bdfa276676c664dc100b voted for 8ce97092515e4e1f53f5bdfa276676c664dc100b 1
redis-sentinel2 | 1:X 23 Jul 2020 09:28:36.211 # +elected-leader master mymaster 172.30.0.2 6379
```

接著 Sentinel2 選出了 redis-slave1(slave 172.30.0.3:6379) 作為 Master，並且下了 `slaveof no one` 的指令使其解除 Slave 狀態變回獨立的 Master，隨後將 redis-slave1 升格為 Master。

```bash
redis-sentinel2 | 1:X 23 Jul 2020 09:28:36.211 # +failover-state-select-slave master mymaster 172.30.0.2 6379
redis-sentinel2 | 1:X 23 Jul 2020 09:28:36.268 # +selected-slave slave 172.30.0.3:6379 172.30.0.3 6379 @ mymaster 172.30.0.2 6379
redis-sentinel2 | 1:X 23 Jul 2020 09:28:36.268 * +failover-state-send-slaveof-noone slave 172.30.0.3:6379 172.30.0.3 6379 @ mymaster 172.30.0.2 6379
redis-sentinel2 | 1:X 23 Jul 2020 09:28:36.339 * +failover-state-wait-promotion slave 172.30.0.3:6379 172.30.0.3 6379 @ mymaster 172.30.0.2 6379
redis-sentinel2 | 1:X 23 Jul 2020 09:28:36.584 # +promoted-slave slave 172.30.0.3:6379 172.30.0.3 6379 @ mymaster 172.30.0.2 6379
```

設定完新的 Master 後，Sentinel2 讓原本的 Master 轉為 Slave，並且讓 redis-slave2(172.30.0.4:6379) 指向新的 Master。

```bash
redis-sentinel2 | 1:X 23 Jul 2020 09:28:36.584 # +failover-state-reconf-slaves master mymaster 172.30.0.2 6379
redis-sentinel2 | 1:X 23 Jul 2020 09:28:36.610 * +slave-reconf-sent slave 172.30.0.4:6379 172.30.0.4 6379 @ mymaster 172.30.0.2 6379
```

Sentinel1 和 Sentinel3 開始從 Sentinel2 取得設定然後更新自己的設定，至此整個故障轉移就完成了。

```bash
redis-sentinel1 | 1:X 23 Jul 2020 09:28:36.611 # +config-update-from sentinel 8ce97092515e4e1f53f5bdfa276676c664dc100b 172.30.0.7 26380 @ mymaster 172.30.0.2 6379
redis-sentinel3 | 1:X 23 Jul 2020 09:28:36.611 # +config-update-from sentinel 8ce97092515e4e1f53f5bdfa276676c664dc100b 172.30.0.7 26380 @ mymaster 172.30.0.2 6379
redis-sentinel3 | 1:X 23 Jul 2020 09:28:36.611 # +switch-master mymaster 172.30.0.2 6379 172.30.0.3 6379
redis-sentinel3 | 1:X 23 Jul 2020 09:28:36.611 * +slave slave 172.30.0.4:6379 172.30.0.4 6379 @ mymaster 172.30.0.3 6379
redis-sentinel3 | 1:X 23 Jul 2020 09:28:36.611 * +slave slave 172.30.0.2:6379 172.30.0.2 6379 @ mymaster 172.30.0.3 6379
redis-sentinel1 | 1:X 23 Jul 2020 09:28:36.611 # +switch-master mymaster 172.30.0.2 6379 172.30.0.3 6379
redis-sentinel1 | 1:X 23 Jul 2020 09:28:36.611 * +slave slave 172.30.0.4:6379 172.30.0.4 6379 @ mymaster 172.30.0.3 6379
redis-sentinel1 | 1:X 23 Jul 2020 09:28:36.611 * +slave slave 172.30.0.2:6379 172.30.0.2 6379 @ mymaster 172.30.0.3 6379
redis-sentinel2 | 1:X 23 Jul 2020 09:28:37.284 # -odown master mymaster 172.30.0.2 6379
redis-sentinel2 | 1:X 23 Jul 2020 09:28:37.284 * +slave-reconf-inprog slave 172.30.0.4:6379 172.30.0.4 6379 @ mymaster 172.30.0.2 6379
redis-sentinel2 | 1:X 23 Jul 2020 09:28:37.284 * +slave-reconf-done slave 172.30.0.4:6379 172.30.0.4 6379 @ mymaster 172.30.0.2 6379
redis-sentinel2 | 1:X 23 Jul 2020 09:28:37.355 # +failover-end master mymaster 172.30.0.2 6379
redis-sentinel2 | 1:X 23 Jul 2020 09:28:37.355 # +switch-master mymaster 172.30.0.2 6379 172.30.0.3 6379
redis-sentinel2 | 1:X 23 Jul 2020 09:28:37.355 * +slave slave 172.30.0.4:6379 172.30.0.4 6379 @ mymaster 172.30.0.3 6379
redis-sentinel2 | 1:X 23 Jul 2020 09:28:37.355 * +slave slave 172.30.0.2:6379 172.30.0.2 6379 @ mymaster 172.30.0.3 6379
```

此時因為 redis-master 還是處於關閉的狀態，所以三個哨兵還是會判斷其為主觀下線，但是因為他已經成為 Slave，所以不會進行故障轉移。

```bash
redis-sentinel1 | 1:X 23 Jul 2020 09:28:41.616 # +sdown slave 172.30.0.2:6379 172.30.0.2 6379 @ mymaster 172.30.0.3 6379
redis-sentinel3 | 1:X 23 Jul 2020 09:28:41.631 # +sdown slave 172.30.0.2:6379 172.30.0.2 6379 @ mymaster 172.30.0.3 6379
redis-sentinel2 | 1:X 23 Jul 2020 09:28:42.402 # +sdown slave 172.30.0.2:6379 172.30.0.2 6379 @ mymaster 172.30.0.3 6379
```

這時再次啟動 redis-master，Sentinel2 會正式的將 redis-master 轉換為 Slave。

```bash
redis-sentinel2 | 1:X 24 Jul 2020 01:02:17.484 * +convert-to-slave slave 172.30.0.2:6379 172.30.0.2 6379 @ mymaster 172.30.0.3 6379
```