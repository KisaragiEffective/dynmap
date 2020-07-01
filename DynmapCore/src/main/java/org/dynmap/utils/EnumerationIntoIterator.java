package org.dynmap.utils;

import java.util.Enumeration;
import java.util.Iterator;

public class EnumerationIntoIterator<T> implements Iterator<T> {
    private final Enumeration<? extends T> enumeration;
    public EnumerationIntoIterator(Enumeration<? extends T> enumeration) {
        this.enumeration = enumeration;
    }

    @Override
    public boolean hasNext() {
        return enumeration.hasMoreElements();
    }

    @Override
    public T next() {
        return enumeration.nextElement();
    }
}