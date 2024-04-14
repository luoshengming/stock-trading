package online.mwang.stockTrading.schedule.jobs;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.schedule.IDataService;
import online.mwang.stockTrading.web.bean.base.BusinessException;
import online.mwang.stockTrading.web.bean.po.AccountInfo;
import online.mwang.stockTrading.web.bean.po.OrderInfo;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.TradingRecord;
import online.mwang.stockTrading.web.mapper.AccountInfoMapper;
import online.mwang.stockTrading.web.service.OrderInfoService;
import online.mwang.stockTrading.web.service.StockInfoService;
import online.mwang.stockTrading.web.service.TradingRecordService;
import online.mwang.stockTrading.web.utils.DateUtils;
import online.mwang.stockTrading.web.utils.SleepUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:22
 * @description: FoundTradingMapper
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunSaleJob extends BaseJob {

    private final IDataService dataService;
    private final TradingRecordService tradingRecordService;
    private final StockInfoService stockInfoService;
    private final OrderInfoService orderInfoService;
    private final AccountInfoMapper accountInfoMapper;
    private final SleepUtils sleepUtils;

    @Override
    public void run() {
        // 查询所有未卖出股票
        LambdaQueryWrapper<TradingRecord> queryWrapper = new LambdaQueryWrapper<TradingRecord>().eq(TradingRecord::getSold, "0");
        List<TradingRecord> tradingRecords = tradingRecordService.list(queryWrapper);
        // 多线程异步卖出
        tradingRecords.forEach(tradingRecord -> new Thread(() -> saleStock(tradingRecord)).start());
    }

    private void saleStock(TradingRecord record) {
        int priceCount = 1;
        double priceTotal = 0.0;
        Double nowPrice;
        while (DateUtils.inTradingTimes1()) {
            sleepUtils.second(30);
            nowPrice = dataService.getNowPrice(record.getCode());
            double priceAvg = priceTotal / priceCount;
            priceTotal += nowPrice;
            priceCount++;
            if (priceCount > 60 && nowPrice > priceAvg + priceAvg * 0.05 || DateUtils.isDeadLine1()) {
                if (DateUtils.isDeadLine1()) log.info("交易时间段即将结束");
                log.info("开始卖出股票");
                JSONObject result = dataService.buySale("S", record.getCode(), nowPrice, record.getBuyNumber());
                String saleNo = result.getString("ANSWERNO");
                Boolean success = dataService.waitOrderStatus(saleNo);
                if (success == null) throw new BusinessException("买入失败，撤单失败，无可卖数量");
                if (!success) {
                    log.info("卖出失败, 撤单成功，继续卖出");
                    continue;
                }
                log.info("卖出成功！");
                // 保存订单信息
                final Date now = new Date();
                OrderInfo orderInfo = new OrderInfo();
                orderInfo.setStatus("1");
                orderInfo.setCode(record.getCode());
                orderInfo.setName(record.getName());
                orderInfo.setPrice(nowPrice);
                orderInfo.setNumber(record.getBuyNumber());
                orderInfo.setType("卖出");
                orderInfo.setAnswerNo(saleNo);
                orderInfo.setCreateTime(now);
                orderInfo.setUpdateTime(now);
                orderInfo.setDate(DateUtils.format1(now));
                orderInfo.setTime(DateUtils.format2(now));
                orderInfoService.save(orderInfo);
                // 保存交易记录
                record.setSold("1");
                record.setSaleNo(saleNo);
                record.setSaleDate(now);
                record.setSaleDateString(DateUtils.dateFormat.format(now));
                record.setUpdateTime(now);
                // 计算收益率并更新交易记录
                final double amount = record.getSalePrice() * record.getSaleNumber();
                double saleAmount = amount - dataService.getPeeAmount(amount);
                double income = saleAmount - record.getBuyAmount();
                double incomeRate = income / record.getBuyAmount() * 100;
                record.setSaleAmount(saleAmount);
                record.setIncome(income);
                record.setIncomeRate(incomeRate);
                record.setHoldDays(1);
                record.setDailyIncomeRate(incomeRate);
                tradingRecordService.updateById(record);
                // 更新账户资金
                AccountInfo accountInfo = dataService.getAccountInfo();
                accountInfo.setCreateTime(new Date());
                accountInfo.setUpdateTime(new Date());
                accountInfoMapper.insert(accountInfo);
                // 增加股票交易次数
                StockInfo stockInfo = stockInfoService.getOne(new LambdaQueryWrapper<StockInfo>().eq(StockInfo::getCode, record.getCode()));
                stockInfo.setBuySaleCount(stockInfo.getBuySaleCount() + 1);
                stockInfoService.updateById(stockInfo);
                log.info("成功卖出股票[{}-{}], 卖出金额为:{}, 收益为:{},日收益率为:{}。", stockInfo.getCode(), stockInfo.getName(), record.getSaleAmount(), record.getIncome(), record.getDailyIncomeRate());
            }

        }
    }

}
