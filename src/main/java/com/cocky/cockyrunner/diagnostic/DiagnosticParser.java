package com.cocky.cockyrunner.diagnostic;

import java.util.List;

/**
 * Extracts {@link Diagnostic}s from one language's compiler/runtime output.
 * Implementations must never throw: unparseable input yields an empty list.
 */
interface DiagnosticParser {

    /**
     * @param output raw (untruncated, un-scrubbed) compiler or stderr text
     * @param source the submitted source, used only to resolve columns that the
     *               output alone can't pin down; may be null
     */
    List<Diagnostic> parse(String output, String source);
}
