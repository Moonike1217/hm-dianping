package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {
    // 满足条件的结果集合
    private List<?> list;
    // 下次查询的最小时间戳
    private Long minTime;
    // 下次查询的偏移量
    private Integer offset;
}
