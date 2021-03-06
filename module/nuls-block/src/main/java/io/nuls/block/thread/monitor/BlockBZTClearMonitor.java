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

import io.nuls.base.data.NulsHash;
import io.nuls.block.model.BlockSaveTemp;
import io.nuls.block.model.BlockVerifyFlag;
import io.nuls.block.model.ChainContext;
import io.nuls.core.log.logback.NulsLogger;
import io.nuls.core.rpc.util.NulsDateUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * bzt校验的区块数据清理情况监控器
 * 每隔固定时间间隔启动
 * 如果发现缓存数据超出100size，则启动时间对比进行数据清理
 *
 * @author ljs
 * @version 1.0
 * @date 19-12-14 下午3:53
 */
public class BlockBZTClearMonitor extends BaseMonitor {
    private static final short MAX_TEMP_SIZE = 100;
    private static final short OVER_TIME_INTERVAL = 100;

    private static final BlockBZTClearMonitor INSTANCE = new BlockBZTClearMonitor();

    public static BlockBZTClearMonitor getInstance() {
        return INSTANCE;
    }

    @Override
    protected void process(int chainId, ChainContext context, NulsLogger commonLog) {
        Map<NulsHash, BlockVerifyFlag> blockVerifyFlagMap = context.getSavingBZTAndVerify();
        Map<NulsHash, BlockSaveTemp> blockSaveTempMap = context.getBlockVerifyResult();
        List<NulsHash> removeHash = new ArrayList<>();
        long nowTime = NulsDateUtils.getCurrentTimeSeconds();
        try {
            if (blockVerifyFlagMap.size() > MAX_TEMP_SIZE) {
                for (Map.Entry<NulsHash, BlockVerifyFlag> entry : blockVerifyFlagMap.entrySet()) {
                    if (nowTime - entry.getValue().getTime() > OVER_TIME_INTERVAL) {
                        removeHash.add(entry.getKey());
                    }
                }
            }
            if (blockSaveTempMap.size() > MAX_TEMP_SIZE) {
                for (Map.Entry<NulsHash, BlockSaveTemp> entry : blockSaveTempMap.entrySet()) {
                    if (nowTime - entry.getValue().getTime() > OVER_TIME_INTERVAL) {
                        removeHash.add(entry.getKey());
                    }
                }
            }
            for (NulsHash hash : removeHash) {
                commonLog.info("clear temp data hash={}", hash);
                blockVerifyFlagMap.remove(hash);
                blockSaveTempMap.remove(hash);
            }
        }catch(Exception e){
            commonLog.error(e);
        }
    }

}
