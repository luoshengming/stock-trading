package online.mwang.stockTrading.web.controller;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.web.bean.base.Response;
import online.mwang.stockTrading.web.bean.po.StockHistoryPrice;
import online.mwang.stockTrading.web.bean.po.StockInfo;
import online.mwang.stockTrading.web.bean.po.StockTestPrice;
import online.mwang.stockTrading.web.bean.query.StockInfoQuery;
import online.mwang.stockTrading.web.bean.vo.Point;
import online.mwang.stockTrading.web.service.StockInfoService;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 10:56
 * @description: FoundTradingController
 */
@Slf4j
@RestController
@RequestMapping("/stockInfo")
@RequiredArgsConstructor
public class StockInfoController {

    private final static String ASCEND = "ascend";
    private final StockInfoService stockInfoService;
    private final MongoTemplate mongoTemplate;

    @GetMapping("/list")
    public Response<List<StockInfo>> listStockInfo(StockInfoQuery query) {
        LambdaQueryWrapper<StockInfo> queryWrapper = new QueryWrapper<StockInfo>().lambda()
                .like(ObjectUtils.isNotNull(query.getCode()), StockInfo::getCode, query.getCode())
                .like(ObjectUtils.isNotNull(query.getName()), StockInfo::getName, query.getName())
                .like(ObjectUtils.isNotNull(query.getMarket()), StockInfo::getMarket, query.getName())
                .eq(ObjectUtils.isNotNull(query.getPermission()), StockInfo::getPermission, query.getPermission())
                .eq(ObjectUtils.isNotNull(query.getBuySaleCount()), StockInfo::getBuySaleCount, query.getBuySaleCount())
                .ge(ObjectUtils.isNotNull(query.getPriceLow()), StockInfo::getPrice, query.getPriceLow())
                .le(ObjectUtils.isNotNull(query.getPriceHigh()), StockInfo::getPrice, query.getPriceHigh())
                .orderBy(true, ASCEND.equals(query.getSortOrder()), StockInfo.getOrder(query.getSortKey()));
        Page<StockInfo> pageResult = stockInfoService.page(Page.of(query.getCurrent(), query.getPageSize()), queryWrapper);
//        List<StockInfo> collect = pageResult.getRecords().stream().peek(o -> {
//            List<DailyItem> priceList = JSON.parseArray(o.getPrices(), DailyItem.class);
//            final double maxPrice = priceList.stream().mapToDouble(DailyItem::getItem).max().orElse(0.0);
//            final double minPrice = priceList.stream().mapToDouble(DailyItem::getItem).min().orElse(0.0);
//            o.setPricesList(priceList);
//            o.setMaxPrice(Math.ceil(maxPrice));
//            o.setMinPrice(Math.floor(minPrice));
//            List<DailyItem> rateList = JSON.parseArray(o.getIncreaseRate(), DailyItem.class);
//            final double maxRate = rateList.stream().mapToDouble(DailyItem::getItem).max().orElse(0.0);
//            final double minRate = rateList.stream().mapToDouble(DailyItem::getItem).min().orElse(0.0);
//            o.setIncreaseRateList(rateList);
//            o.setMaxIncrease(maxRate);
//            o.setMinIncrease(minRate);
//        }).collect(Collectors.toList());
        return Response.success(pageResult.getRecords(), pageResult.getTotal());
    }


    @GetMapping("/listHistoryPrices")
    public Response<JSONObject> listHistoryPrices(StockInfoQuery param) {
        String stockCode = param.getCode();
        Query query = new Query(Criteria.where("code").is(stockCode)).with(Sort.by(Sort.Direction.ASC, "date"));
        List<StockHistoryPrice> stockHistoryPrices = mongoTemplate.find(query, StockHistoryPrice.class);
        JSONObject data = new JSONObject();
        data.put("points", stockHistoryPrices);
        double maxValue = stockHistoryPrices.stream().mapToDouble(StockHistoryPrice::getPrice1).max().orElse(0.0);
        data.put("maxValue", maxValue);
        double minValue = stockHistoryPrices.stream().mapToDouble(StockHistoryPrice::getPrice1).min().orElse(0.0);
        data.put("minValue", minValue);
        return Response.success(data);
    }

    @GetMapping("/listTestPrices")
    public Response<JSONObject> listTestPrices(StockInfoQuery param) {
        String stockCode = param.getCode();
        // 查找测试集数据
        final Query query = new Query(Criteria.where("code").is(stockCode)).with(Sort.by(Sort.Direction.ASC, "date"));
        List<StockTestPrice> stockTestPrices = mongoTemplate.find(query, StockTestPrice.class);
        String maxDate = stockTestPrices.stream().map(StockTestPrice::getDate).max(String::compareTo).orElse("");
        String minDate = stockTestPrices.stream().map(StockTestPrice::getDate).min(String::compareTo).orElse("");
        // 查找历史数据
        Query historyQuery = new Query(Criteria.where("code").is(stockCode).and("date").lte(maxDate).gte(minDate));
        List<StockHistoryPrice> stockHistoryPrices = mongoTemplate.find(historyQuery, StockHistoryPrice.class);
        final ArrayList<Point> points = new ArrayList<>();
        for (int i = 0; i < stockTestPrices.size(); i++) {
            StockTestPrice stockTestPrice = stockTestPrices.get(i);
            StockHistoryPrice stockHistoryPrice = stockHistoryPrices.get(i);
            final Point point1 = new Point(stockTestPrice.getDate(), stockTestPrice.getPrice1());
            final Point point2 = new Point(stockHistoryPrice.getDate(), stockHistoryPrice.getPrice1());
            point1.setType("预测开盘价");
            point2.setType("实际开盘价");
            points.add(point1);
            points.add(point2);
        }
        JSONObject data = new JSONObject();
        data.put("points", points);
        double maxValue = points.stream().mapToDouble(Point::getY).max().orElse(0.0);
        data.put("maxValue", maxValue);
        double minValue = points.stream().mapToDouble(Point::getY).min().orElse(0.0);
        data.put("minValue", minValue);
        return Response.success(data);
    }
}
