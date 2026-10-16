package org.opendatamesh.platform.adapter.marketplace.executor.starter.utils.repositories;

import java.util.ArrayList;
import java.util.List;

public final class Sort {

    public enum Direction {
        ASC,
        DESC
    }

    public static final class Order {
        private final Direction direction;
        private final String property;

        public Order(Direction direction, String property) {
            this.direction = direction;
            this.property = property;
        }

        public Direction getDirection() {
            return direction;
        }

        public String getProperty() {
            return property;
        }
    }

    private final List<Order> orders;

    private Sort(List<Order> orders) {
        this.orders = List.copyOf(orders);
    }

    public static Sort unsorted() {
        return new Sort(List.of());
    }

    public static Sort by(String property) {
        return by(Direction.ASC, property);
    }

    public static Sort of(List<Order> orders) {
        return new Sort(orders == null ? List.of() : orders);
    }

    public static Sort by(Direction direction, String... properties) {
        List<Order> parsed = new ArrayList<>();
        if (properties != null) {
            for (String property : properties) {
                parsed.add(new Order(direction, property));
            }
        }
        return new Sort(parsed);
    }

    public boolean isUnsorted() {
        return orders.isEmpty();
    }

    public boolean isSorted() {
        return !orders.isEmpty();
    }

    public List<Order> getOrders() {
        return orders;
    }
}
