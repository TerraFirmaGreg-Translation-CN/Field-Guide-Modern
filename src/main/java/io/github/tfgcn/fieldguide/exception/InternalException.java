package io.github.tfgcn.fieldguide.exception;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class InternalException extends RuntimeException {
    private final boolean quiet;
    
    public InternalException(String reason, boolean quiet) {
        super(reason);
        this.quiet = quiet;
    }

    public InternalException(String reason) {
        this(reason, false);
    }
}