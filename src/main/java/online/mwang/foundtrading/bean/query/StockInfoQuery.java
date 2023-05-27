package online.mwang.foundtrading.bean.query;

import lombok.Data;
import online.mwang.foundtrading.bean.base.BaseQuery;

/**
 * @version 1.0.0
 * @author: mwangli
 * @date: 2023/3/20 13:30
 * @description: FoundTradingQuery
 */

@Data
public class StockInfoQuery extends BaseQuery {

    private String name;

    private String code;

    private String market;

    private String permission;

    private Integer buySaleCount;

    private Double priceLow;

    private Double priceHigh;
}
