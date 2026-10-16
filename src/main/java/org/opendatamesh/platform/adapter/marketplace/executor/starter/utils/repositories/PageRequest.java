package org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.repositories;

public final class PageRequest {

    private final int page;
    private final int size;
    private final Sort sort;

    private PageRequest(int page, int size, Sort sort) {
        this.page = page;
        this.size = size;
        this.sort = sort == null ? Sort.unsorted() : sort;
    }

    public static PageRequest of(int page, int size) {
        return new PageRequest(page, size, Sort.unsorted());
    }

    public static PageRequest of(int page, int size, Sort sort) {
        return new PageRequest(page, size, sort);
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public Sort getSort() {
        return sort;
    }
}
