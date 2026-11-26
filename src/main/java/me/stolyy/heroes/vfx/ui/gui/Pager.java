package me.stolyy.heroes.vfx.ui.gui;

import java.util.List;

/** Small index calculator for paginated inventory strips and grids. */
public final class Pager {
    private final int firstPageCap;
    private final int otherPageCap;
    private int page;

    public Pager(int pageCap) {
        this(pageCap, pageCap);
    }

    public Pager(int firstPageCap, int otherPageCap) {
        if (firstPageCap <= 0 || otherPageCap <= 0) {
            throw new IllegalArgumentException("Page capacities must be positive");
        }
        this.firstPageCap = firstPageCap;
        this.otherPageCap = otherPageCap;
    }

    public int page() {
        return page;
    }

    public void reset() {
        page = 0;
    }

    public int startIndex() {
        return page == 0 ? 0 : firstPageCap + (page - 1) * otherPageCap;
    }

    public int pageCap() {
        return page == 0 ? firstPageCap : otherPageCap;
    }

    public void clamp(int totalItems) {
        while (page > 0 && startIndex() >= totalItems) {
            page--;
        }
    }

    public boolean hasPrev() {
        return page > 0;
    }

    public boolean hasNext(int totalItems) {
        return startIndex() + pageCap() < totalItems;
    }

    public int remainingAfter(int totalItems) {
        return Math.max(0, totalItems - (startIndex() + pageCap()));
    }

    public boolean prev() {
        if (!hasPrev()) return false;
        page--;
        return true;
    }

    public boolean next(int totalItems) {
        if (!hasNext(totalItems)) return false;
        page++;
        return true;
    }

    public <T> List<T> slice(List<T> items) {
        clamp(items.size());
        int start = startIndex();
        int end = Math.min(items.size(), start + pageCap());
        return items.subList(start, end);
    }
}
