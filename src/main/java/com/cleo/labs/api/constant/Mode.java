package com.cleo.labs.api.constant;

import com.cleo.lexicom.external.LexiComFactory;

public enum Mode {
    CLIENT          (LexiComFactory.CLIENT_ONLY),
    SERVER          (LexiComFactory.SERVER_ONLY),
    DISTRIBUTED     (LexiComFactory.CLIENT_OR_SERVER),
    @SuppressWarnings("deprecation")
    STANDALONE      (LexiComFactory.STANDALONE),
    @SuppressWarnings("deprecation")
    STANDALONE_WAIT (LexiComFactory.STANDALONE_WAIT);

    public final int id;
    private Mode(int id) {
        this.id = id;
    }
}
