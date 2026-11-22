package com.wechatarchive.domain.importer;

public final class MoectImportException extends Exception {
    public MoectImportException(String message) {
        super(message);
    }

    public MoectImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
