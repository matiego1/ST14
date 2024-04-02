package me.matiego.st14.objects;

import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

public class Pair<F, S> {

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    @Getter @Setter private F first;
    @Getter @Setter private S second;

    @Override
    public String toString() {
        return "(" + getFirst() + ", " + getSecond() + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(first, pair.first) && Objects.equals(second, pair.second);
    }
}
