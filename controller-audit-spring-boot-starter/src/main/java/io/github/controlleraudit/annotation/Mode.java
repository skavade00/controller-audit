package io.github.controlleraudit.annotation;

/** How an audit event is written to the sinks. */
public enum Mode {
    /** Use the next level up (class, then global default). */
    INHERIT,
    /** Write inside the request thread. Slower, but nothing is lost on crash. */
    SYNC,
    /** Queue the event and write it from background workers (default). */
    ASYNC
}
