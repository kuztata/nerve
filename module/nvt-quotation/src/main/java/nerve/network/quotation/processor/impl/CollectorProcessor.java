/**
 * MIT License
 * <p>
 * Copyright (c) 2019-2020 nerve.network
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

package nerve.network.quotation.processor.impl;

import io.nuls.core.core.annotation.Component;
import io.nuls.core.core.ioc.SpringLiteContext;
import nerve.network.quotation.constant.QuotationConstant;
import nerve.network.quotation.model.bo.Chain;
import nerve.network.quotation.model.bo.QuerierCfg;
import nerve.network.quotation.processor.Collector;
import nerve.network.quotation.rpc.querier.Querier;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @author: Chino
 * @date: 2020/03/5
 */
@Component
public class CollectorProcessor implements Collector {

    @Override
    public BigDecimal enquiry(Chain chain, String anchorToken) {
        try {
            /**
             * 根据token从各个查询器采集多个(交易所等)价格
             * 根据不同第三方机构,对价格计算加权平均值
             */
            chain.getLogger().info("开始获({})第三方报价", anchorToken);
            BigDecimal interim = new BigDecimal("0");
            BigDecimal weightTotal = new BigDecimal("0.0");
            for (QuerierCfg cfg : chain.getCollectors()) {
                try {
                    Querier querier = getQuerier(cfg.getCollector());
                    BigDecimal price = querier.tickerPrice(chain, cfg.getBaseurl(), anchorToken);
                    if(null == price){
                        continue;
                    }
                    BigDecimal weight = new BigDecimal(cfg.getWeight());
                    interim = interim.add(price.multiply(weight));
                    weightTotal = weightTotal.add(weight);
                } catch (Exception e) {
                    chain.getLogger().error("获取价格异常, name:{}, anchorToken:{}", cfg.getName(), anchorToken);
                    chain.getLogger().error(e);
                    continue;
                }
            }
            if(weightTotal.doubleValue() == 0){
                chain.getLogger().error("没有获取token任何第三方价格, anchorToken:{}", anchorToken);
                return null;
            }
            return interim.divide(weightTotal, QuotationConstant.SCALE, RoundingMode.HALF_DOWN);
        } catch (Throwable e) {
            chain.getLogger().error("获取token第三方价格失败, anchorToken:{}", anchorToken);
            chain.getLogger().error(e);
            return null;
        }
    }


    public Querier getQuerier(String clazz) throws Exception {
        Class<?> clasz = Class.forName(clazz);
        return (Querier) SpringLiteContext.getBean(clasz);
    }
}
