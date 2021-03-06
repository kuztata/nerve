/**
 * MIT License
 * <p>
 Copyright (c) 2019-2020 nerve.network
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package nerve.network.converter.core.context;

import nerve.network.converter.config.ConverterConfig;
import nerve.network.converter.constant.ConverterErrorCode;
import nerve.network.converter.core.heterogeneous.register.HeterogeneousChainRegister;
import nerve.network.converter.core.heterogeneous.register.interfaces.IHeterogeneousChainRegister;
import nerve.network.converter.manager.ChainManager;
import nerve.network.converter.model.bo.Chain;
import nerve.network.converter.model.bo.HeterogeneousChainInfo;
import nerve.network.converter.model.bo.HeterogeneousChainRegisterInfo;
import nerve.network.converter.storage.HeterogeneousChainInfoStorageService;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.core.ioc.SpringLiteContext;
import io.nuls.core.exception.NulsException;
import io.nuls.core.log.logback.NulsLogger;
import io.nuls.core.model.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author: Chino
 * @date: 2020-02-18
 */
@Component
public class HeterogeneousChainManager {

    @Autowired
    private ConverterConfig converterConfig;
    @Autowired
    private ChainManager chainManager;
    @Autowired
    private HeterogeneousChainInfoStorageService heterogeneousChainInfoStorageService;
    @Autowired
    private HeterogeneousChainRegister heterogeneousChainRegister;
    private boolean isInited = false;

    private final Map<String, Integer> heterogeneousChainIdRuleMap = new HashMap<>();

    private Map<Integer, HeterogeneousChainInfo> heterogeneousChainMap = new ConcurrentHashMap<>();

    public HeterogeneousChainInfo getHeterogeneousChainByChainId(Integer chainId) {
        return heterogeneousChainMap.get(chainId);
    }

    private NulsLogger logger() {
        return chainManager.getChain(converterConfig.getChainId()).getLogger();
    }

    /**
     * 根据异构链名称获取chainId
     */
    public int getHeterogeneousChainIdByName(String heterogeneousChainName) throws NulsException {
        heterogeneousChainName = heterogeneousChainName.trim().toLowerCase();
        Integer chainId = heterogeneousChainIdRuleMap.get(heterogeneousChainName);
        if (chainId == null) {
            logger().error("error heterogeneousChainName: {}", heterogeneousChainName);
            throw new NulsException(ConverterErrorCode.HETEROGENEOUS_CHAIN_NAME_ERROR);
        }
        return chainId;
    }

    public Map<Integer, HeterogeneousChainInfo> getHeterogeneousChainMap() {
        return heterogeneousChainMap;
    }

    /**
     * //TODO pierre 异构链多签地址变更后，调用此函数持久化更新
     * 更新异构链多签地址
     */
    public void updateMultySignAddress(int heterogeneousChainId, String multySignAddress) throws NulsException {
        HeterogeneousChainInfo info = heterogeneousChainInfoStorageService.getHeterogeneousChainInfo(heterogeneousChainId);
        if (info == null) {
            logger().error("error heterogeneousChainId: {}", heterogeneousChainId);
            throw new NulsException(ConverterErrorCode.HETEROGENEOUS_CHAINID_ERROR);
        }
        info.setMultySignAddress(multySignAddress);
        try {
            heterogeneousChainInfoStorageService.saveHeterogeneousChainInfo(heterogeneousChainId, info);
        } catch (Exception e) {
            throw new NulsException(ConverterErrorCode.DB_SAVE_ERROR, e);
        }
        heterogeneousChainMap.get(heterogeneousChainId).setMultySignAddress(multySignAddress);
    }

    /**
     * 初始化所有异构链信息
     */
    public void initHeterogeneousChainInfo() throws Exception {
        if (!isInited) {
            Chain chain = chainManager.getChain(converterConfig.getChainId());
            isInited = true;
            List<HeterogeneousChainInfo> storageList = heterogeneousChainInfoStorageService.getAllHeterogeneousChainInfoList();
            if (storageList != null && !storageList.isEmpty()) {
                storageList.stream().forEach(info -> heterogeneousChainMap.put(info.getChainId(), info));
            }

            Collection<Object> list = SpringLiteContext.getAllBeanList();
            for (Object object : list) {
                if(object instanceof IHeterogeneousChainRegister) {
                    IHeterogeneousChainRegister register = (IHeterogeneousChainRegister) object;
                    int chainId = register.getChainId();
                    // 执行异构链的初始化函数
                    register.init(chain.getHeterogeneousCfg(chainId, 1), chain.getLogger());
                    HeterogeneousChainInfo chainInfo = register.getChainInfo();
                    // 保存异构链symbol和chainId的关系
                    heterogeneousChainIdRuleMap.put(chainInfo.getChainName(), chainId);
                    String multySignAddress = chainInfo.getMultySignAddress();
                    // 持久化存储异构链基本信息
                    if (StringUtils.isNotBlank(multySignAddress) && !heterogeneousChainMap.containsKey(chainId)) {
                        try {
                            heterogeneousChainInfoStorageService.saveHeterogeneousChainInfo(chainId, chainInfo);
                        } catch (Exception e) {
                            throw new NulsException(ConverterErrorCode.DB_SAVE_ERROR, e);
                        }
                        heterogeneousChainMap.put(chainId, chainInfo);
                    }
                    // 执行异构链注册
                    HeterogeneousChainRegisterInfo registerInfo = heterogeneousChainRegister.register(converterConfig.getChainId(), chainId, register.getDockingImpl());
                    // 向异构链组件返回注册信息
                    register.registerCallBack(registerInfo);
                }
            }
        }

    }
}
