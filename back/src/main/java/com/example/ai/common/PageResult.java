package com.example.ai.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;
import java.util.function.Function;

@Data
public class PageResult<T> {

    private long total;
    private long pages;
    private long pageNum;
    private long pageSize;
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        return of(page, Function.identity());
    }

    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> converter) {
        PageResult<T> result = new PageResult<>();
        result.total = page.getTotal();
        result.pages = page.getPages();
        result.pageNum = page.getCurrent();
        result.pageSize = page.getSize();
        result.records = page.getRecords().stream().map(converter).toList();
        return result;
    }
}