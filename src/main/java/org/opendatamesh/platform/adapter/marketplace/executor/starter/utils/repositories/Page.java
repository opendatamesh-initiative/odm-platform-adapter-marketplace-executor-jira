package org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.repositories;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@RegisterForReflection
public final class Page<T> {

    private final List<T> content;
    private final int number;
    private final int size;
    private final long totalElements;

    public Page(List<T> content, int number, int size, long totalElements) {
        this.content = List.copyOf(content);
        this.number = number;
        this.size = size;
        this.totalElements = totalElements;
    }

    public List<T> getContent() {
        return content;
    }

    public int getNumber() {
        return number;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        if (size <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / (double) size);
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    public <R> Page<R> map(Function<T, R> mapper) {
        List<R> mapped = new ArrayList<>(content.size());
        for (T item : content) {
            mapped.add(mapper.apply(item));
        }
        return new Page<>(mapped, number, size, totalElements);
    }
}
