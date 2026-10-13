package com.layue13.clashweave.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ContentValidationException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;
    private final List<String> errors;

    ContentValidationException(List<String> errors) {
        super(String.join("\n", errors));
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public List<String> errors() {
        return errors;
    }
}
