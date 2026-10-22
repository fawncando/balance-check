package com.bzd.balance.common;

import java.util.List;

/** 分页结果：列表接口统一返回当前页数据与总数。 */
public class PageView<T> {

    private List<T> items;
    private long total;
    private int page;
    private int size;

    public PageView() {
    }

    public PageView(List<T> items, long total, int page, int size) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public List<T> getItems() { return items; }
    public void setItems(List<T> items) { this.items = items; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
