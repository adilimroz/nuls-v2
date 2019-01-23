/*
 * MIT License
 * Copyright (c) 2017-2019 nuls.io
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.nuls.block.thread.monitor;

import io.nuls.block.constant.RunningStatusEnum;
import io.nuls.block.manager.ChainManager;
import io.nuls.block.manager.ContextManager;
import io.nuls.block.model.Chain;
import io.nuls.block.model.ChainContext;
import io.nuls.block.model.ChainParameters;
import io.nuls.block.utils.module.ConsensusUtil;

import java.util.SortedSet;
import java.util.concurrent.locks.StampedLock;

import static io.nuls.block.constant.Constant.CONSENSUS_WAITING;
import static io.nuls.block.constant.Constant.CONSENSUS_WORKING;
import static io.nuls.block.utils.LoggerUtil.commonLog;

/**
 * 分叉链的形成原因分析:由于网络延迟,同时有两个矿工发布同一高度的区块,或者被恶意节点攻击
 * 分叉链定时处理器,如果发现某分叉链比主链更长,需要切换该分叉链为主链
 *
 * @author captain
 * @version 1.0
 * @date 18-11-14 下午3:54
 */
public class ForkChainsMonitor implements Runnable {

    private static final ForkChainsMonitor INSTANCE = new ForkChainsMonitor();

    private ForkChainsMonitor() {

    }

    public static ForkChainsMonitor getInstance() {
        return INSTANCE;
    }

    @Override
    public void run() {
        for (Integer chainId : ContextManager.chainIds) {
            ChainContext context = ContextManager.getContext(chainId);
            try {
                //判断该链的运行状态,只有正常运行时才会有分叉链的处理
                RunningStatusEnum status = context.getStatus();
                if (!status.equals(RunningStatusEnum.RUNNING)) {
                    commonLog.debug("skip process, status is " + status + ", chainId-" + chainId);
                    continue;
                }

                StampedLock lock = context.getLock();
                long stamp = lock.tryOptimisticRead();
                try {
                    for (; ; stamp = lock.writeLock()) {
                        if (stamp == 0L) {
                            continue;
                        }
                        // possibly racy reads
                        SortedSet<Chain> forkChains = ChainManager.getForkChains(chainId);
                        if (!lock.validate(stamp)) {
                            continue;
                        }
                        if (forkChains.size() < 1) {
                            break;
                        }
                        commonLog.info("####################################fork chains######################################");
                        for (Chain forkChain : forkChains) {
                            commonLog.info("#" + forkChain);
                        }
                        //遍历当前分叉链,与主链进行比对,找出最大高度差,与默认参数chainSwtichThreshold对比,确定要切换的分叉链
                        Chain masterChain = ChainManager.getMasterChain(chainId);
                        ChainParameters parameters = context.getParameters();
                        int chainSwtichThreshold = parameters.getChainSwtichThreshold();
                        Chain switchChain = new Chain();
                        int maxHeightDifference = 0;
                        for (Chain forkChain : forkChains) {
                            int temp = (int) (forkChain.getEndHeight() - masterChain.getEndHeight());
                            if (temp > maxHeightDifference) {
                                maxHeightDifference = temp;
                                switchChain = forkChain;
                            }
                        }
                        commonLog.debug("chainId-" + chainId + ", maxHeightDifference:" + maxHeightDifference + ", chainSwtichThreshold:" + chainSwtichThreshold);
                        //高度差不够
                        if (maxHeightDifference < chainSwtichThreshold) {
                            break;
                        }
                        stamp = lock.tryConvertToWriteLock(stamp);
                        if (stamp == 0L) {
                            continue;
                        }
                        // exclusive access
                        //进行切换,切换前变更模块运行状态
                        context.setStatus(RunningStatusEnum.SWITCHING);
                        ConsensusUtil.notice(chainId, CONSENSUS_WAITING);
                        if (ChainManager.switchChain(chainId, masterChain, switchChain)) {
                            commonLog.info("chainId-" + chainId + ", switchChain success");
                        } else {
                            commonLog.info("chainId-" + chainId + ", switchChain fail, auto rollback success");
                        }
                        ConsensusUtil.notice(chainId, CONSENSUS_WORKING);
                        break;
                    }
                } finally {
                    context.setStatus(RunningStatusEnum.RUNNING);
                    if (StampedLock.isWriteLockStamp(stamp)) {
                        lock.unlockWrite(stamp);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                context.setStatus(RunningStatusEnum.RUNNING);
                commonLog.error("chainId-" + chainId + ", switchChain fail, auto rollback fail");
            }
        }
    }

}