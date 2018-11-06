/*
 * MIT License
 *
 * Copyright (c) 2017-2018 nuls.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package io.nuls.network.manager;


import io.nuls.network.constant.NetworkParam;
import io.nuls.network.manager.threads.NetworkThreadPool;
import io.nuls.network.manager.threads.NodesConnectThread;

import io.nuls.network.model.Node;

import io.nuls.network.model.dto.IpAddress;
import io.nuls.network.netty.NettyServer;
import io.nuls.tools.log.Log;
import io.nuls.tools.thread.commom.NulsThreadFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
/**
 * 连接管理器
 * connection  manager
 * @author lan
 * @date 2018/11/01
 *
 */
public class ConnectionManager {
    private static ConnectionManager instance = new ConnectionManager();
    /**
     *作为Server 被动连接的peer
     */
    private Map<String, Node> cacheConnectNodeInMap=new ConcurrentHashMap<>();
    /**
     * 作为client 主动连接的peer
     */
    private  Map<String, Node> cacheConnectNodeOutMap=new ConcurrentHashMap<>();

    public void removeCacheConnectNodeOutMap(String nodeKey){
        Node node=cacheConnectNodeOutMap.get(nodeKey);
        node.disConnectNodeChannel();
        cacheConnectNodeOutMap.remove(nodeKey);

    }

    public void removeCacheConnectNodeInMap(String nodeKey){
        Node node=cacheConnectNodeOutMap.get(nodeKey);
        node.disConnectNodeChannel();
        cacheConnectNodeInMap.remove(nodeKey);
    }
    public Node getNodeByCache(String nodeId,int nodeType)
    {
        if(Node.OUT == nodeType){
            return cacheConnectNodeOutMap.get(nodeId);
        }else{
            return cacheConnectNodeInMap.get(nodeId);
        }
    }

    public List<Node> getCacheAllNodeList(){
        List<Node> nodesList=new ArrayList<>();
        nodesList.addAll(cacheConnectNodeInMap.values());
        nodesList.addAll(cacheConnectNodeOutMap.values());
        return nodesList;
    }
    /**
     * 处理已经成功连接的节点
     */
    public boolean processConnectedServerNode(Node node) {
        try {
            cacheConnectNodeInMap.put(node.getId(),node);
            return true;
        } finally {
        }
    }
    public boolean processConnectedClientNode(Node node) {
        try {
            cacheConnectNodeOutMap.put(node.getId(),node);
            return true;
        } finally {

        }
    }
    private ConnectionManager() {
    }

    public void nettyBoot(){
        serverStart();
        clientStart();
    }
    public static ConnectionManager getInstance() {
        return instance;
    }

    public void serverStart(){
        NettyServer server=new NettyServer(NetworkParam.getInstance().getPort());
        NettyServer serverCross=new NettyServer(NetworkParam.getInstance().getCrossPort());
        server.init();
        serverCross.init();
        TaskManager.createAndRunThread("node server start", new Runnable() {
            @Override
            public void run() {
                try {
                    server.start();
                } catch (InterruptedException e) {
                    Log.error(e);
                }
            }
        }, false);
        TaskManager.createAndRunThread("node crossServer start", new Runnable() {
            @Override
            public void run() {
                try {
                    serverCross.start();
                } catch (InterruptedException e) {
                    Log.error(e);
                }
            }
        }, false);

    }

    public void clientStart() {
        ScheduledThreadPoolExecutor executor = TaskManager.createScheduledThreadPool(1, new NulsThreadFactory("NodesConnectThread"));
        executor.scheduleAtFixedRate(new NodesConnectThread(), 5, 10, TimeUnit.SECONDS);
    }

    public void connectionNode(Node node) {
        //发起连接
        NetworkThreadPool.doConnect(node);
    }
    //自我连接
    public void selfConnection(){
        if(LocalInfoManager.getInstance().isConnectedMySelf()){
            return;
        }
        IpAddress ipAddress=LocalInfoManager.getInstance().getExternalAddress();
        Node node=new Node(ipAddress.getIp().getHostAddress(),ipAddress.getPort(),Node.OUT,false);
        NetworkThreadPool.doConnect(node);
        LocalInfoManager.getInstance().setConnectedMySelf(true);
    }
}
