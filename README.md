# Redis 主從複製模式實作範例

主從複製模式提供讓 **Master(主伺服器)** 向任意數量的 **Slave(附屬伺服器)** 進行資料同步，以解決單點資料庫的問題。

主從複製模式的最主要目的是實現讀寫分離和資料備份。Redis 讀寫分離的作法是 Master 可以進行讀取和寫入，其他的 Slave 只能讀取。透過 Slave 幫忙處理讀取的工作來減輕 Master 的負擔。

## 工作流程

1. **Slave啟動後會主動向Master發送Sync命令要求進行同步。**
2. **Master收到後會執行Bgsave命令建立rdb快照檔案儲存到硬碟。** 同時，Master會把新收到的寫入和修改資料庫的命令存到緩衝區。
3. **Master會將剛建立好的快照檔案傳給Slave。**
4. **Slave收到快照檔案後會先把記憶體清空，接著載入收到的快照檔案。**
5. **Master會再把存在緩衝區的命令傳給Slave，Slave再執行這些命令以達成和Master同步。**
6. 完成Slave資料初始化後，Master每執行一道寫入或修改資料庫的命令都會傳送給Slave以達到資料的同步。
7. **Master和Slave會互相發送heartbeat訊息，也就是傳送Ping指令。** 以告知對方我還正常運作，也檢查對方是否還正常運作。

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