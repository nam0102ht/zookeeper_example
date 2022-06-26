package com.ntnn;

import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
public class ZookeeperMain implements Watcher  {
    public static final String HOSTNAME = "0.0.0.0:2181";
    public static final int TIMEOUT = 3000;
    public static final String ELECTION_NAMESPACE = "/election";

    public static final String TARGET_NODE = "/target_znode";

    private ZooKeeper zooKeeper;
    private String currentLeaderName;

    @Override
    public void process(WatchedEvent event) {
        switch (event.getType()) {
            case None:
                switch (event.getState()) {
                    case SyncConnected:
                        log.info("{}", "Connecting succeed");
                        break;
                    case Closed:
                        synchronized (zooKeeper) {
                            log.info("Closed connect");
                            this.zooKeeper.notifyAll();
                        }
                        break;
                    default:
                        log.info("No state is found");
                        break;
                }
                break;
            case NodeDeleted:
                try {
                    electLeader();
                } catch (InterruptedException | KeeperException e) {
                    log.error("{}", e.getMessage(), e);
                }
                break;
            default:
                log.info("No type is found");
                break;
        }
        try {
            watchedTargetNode();
        } catch (InterruptedException | KeeperException e) {
            log.error("{}", e.getMessage(), e);
        }
    }

    public void connectedToZookeeper() throws IOException {
        this.zooKeeper = new ZooKeeper(HOSTNAME, TIMEOUT, this);
    }

    public void volunteerLeader() throws InterruptedException, KeeperException {
        String suffixLeader = ELECTION_NAMESPACE + "/c_";
        String znodeFullPath = zooKeeper.create(suffixLeader, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        log.info("ZNode name: {}", znodeFullPath);
        this.currentLeaderName = znodeFullPath.replace(ELECTION_NAMESPACE + "/", "");
    }

    public void watchedTargetNode() throws InterruptedException, KeeperException {
        Stat stat = zooKeeper.exists(TARGET_NODE, this);
        if (stat == null) {
            return;
        }
        byte[] data = zooKeeper.getData(TARGET_NODE, this, stat);
        List<String> children = zooKeeper.getChildren(TARGET_NODE, this);
        log.info("Data: {}, children: {}", new String(data), children);
    }
    public void electLeader() throws InterruptedException, KeeperException {
        String predecessorName = "";
        Stat predecessorStat = null;
        List<String> children = this.zooKeeper.getChildren(ELECTION_NAMESPACE, false);
        Collections.sort(children);
        String smallestChildren = children.get(0);
        while (predecessorStat == null) {
            if (smallestChildren.equals(this.currentLeaderName)) {
                log.info("I am a leader");
                return;
            } else {
                log.info("I'm not a leader, the leader is: " + smallestChildren);
                int predecessorIndex = Collections.binarySearch(children, currentLeaderName) - 1;
                predecessorName = children.get(predecessorIndex);
                predecessorStat = zooKeeper.exists(ELECTION_NAMESPACE + "/" + predecessorName, this);
            }
        }
        log.info("Watching znode: {}", predecessorName);
    }

    public void runConnection() throws InterruptedException {
        synchronized (zooKeeper) {
            zooKeeper.wait();
        }
    }

    public void closeConnection() throws InterruptedException {
        this.zooKeeper.close();
    }

//    public static void main(String[] args) {
//        try {
//            ZookeeperMain zookeeperMain = new ZookeeperMain();
//            zookeeperMain.connectedToZookeeper();
//            zookeeperMain.volunteerLeader();
//            zookeeperMain.electLeader();
//            zookeeperMain.watchedTargetNode();
//            zookeeperMain.runConnection();
//            zookeeperMain.closeConnection();
//        } catch(IOException | InterruptedException ex) {
//            log.error("Ex: {}", ex.getMessage(), ex);
//        } catch (KeeperException e) {
//            log.error("Ex: {}", e.getMessage(), e);
//        }
//    }
}
