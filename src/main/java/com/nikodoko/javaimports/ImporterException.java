package com.nikodoko.javaimports;

import static java.util.Locale.ENGLISH;

import java.util.List;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/** Exception class for importer errors */
public class ImporterException extends Exception {
  private List<ImporterDiagnostic> diagnostics;

  /**
   * Creates a new exception
   *
   * @param diagnostics a list of parser diagnostics
   */
  public static ImporterException fromDiagnostics(
      List<Diagnostic<? extends JavaFileObject>> diagnostics) {
    return new ImporterException(
        diagnostics.stream().map(ImporterDiagnostic::create).collect(Collectors.toList()));
  }

  private ImporterException(List<ImporterDiagnostic> diagnostics) {
    this.diagnostics = diagnostics;
  }

  /**
   * Getter.
   *
   * @return the diagnostics
   */
  public List<ImporterDiagnostic> diagnostics() {
    return diagnostics;
  }

  /** Wrapper class for parser diagnostics */
  public static class ImporterDiagnostic {
    private final int line;
    private final int column;
    private final String message;

    /**
     * Wrap a parser diagnostic
     *
     * @param d the diagnostic to wrap
     */
    public static ImporterDiagnostic create(Diagnostic<?> d) {
      return new ImporterDiagnostic(
          (int) d.getLineNumber(), (int) d.getColumnNumber(), d.getMessage(ENGLISH));
    }

    private ImporterDiagnostic(int line, int column, String message) {
      // TODO: assert > 0 with precondition
      this.line = line;
      this.column = column;
      this.message = message;
    }

    public String toString() {
      // TODO: format this more cleanly
      return line + ":" + column + ": error: " + message;
    }
  }
}
