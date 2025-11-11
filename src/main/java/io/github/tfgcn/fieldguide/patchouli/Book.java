package io.github.tfgcn.fieldguide.patchouli;

import lombok.Getter;

@Getter
public class Book {
    private final String modId;
    private final String bookId;
    private final String source;
    
    public Book(String modId, String bookId, String source) {
        this.modId = modId;
        this.bookId = bookId;
        this.source = source;
    }

    @Override
    public String toString() {
        return "Book: " + modId + ":" + bookId + " (from " + source + ")";
    }
}