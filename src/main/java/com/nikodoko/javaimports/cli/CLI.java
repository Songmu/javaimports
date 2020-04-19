package com.nikodoko.javaimports.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.nikodoko.javaimports.Importer;
import com.nikodoko.javaimports.ImporterException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/** The main class for the CLI */
public final class CLI {
  private final PrintWriter errWriter;
  private final PrintWriter outWriter;

  private CLI(PrintWriter outWriter, PrintWriter errWriter) {
    this.errWriter = errWriter;
    this.outWriter = outWriter;
  }

  static String versionString() {
    return "javaimports: Version " + CLI.class.getPackage().getImplementationVersion();
  }

  /**
   * Main method
   *
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    int result;
    PrintWriter err = new PrintWriter(new OutputStreamWriter(System.err, UTF_8));
    PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, UTF_8));
    try {
      CLI parser = new CLI(out, err);
      result = parser.parse(args);
    } catch (UsageException e) {
      err.print(e.getMessage());
      result = 0;
    } finally {
      err.flush();
      out.flush();
    }

    System.exit(result);
  }

  private CLIOptions processArgs(String... args) throws UsageException {
    CLIOptions params;
    try {
      params = CLIOptionsParser.parse(Arrays.asList(args));
    } catch (IllegalArgumentException e) {
      throw new UsageException(e.getMessage());
    }

    if (params.file() == null && !(params.help() || params.version())) {
      throw new UsageException("please provide a file");
    }

    return params;
  }

  private int parse(String... args) throws UsageException {
    CLIOptions params = processArgs(args);

    if (params.version()) {
      errWriter.println(versionString());
      return 0;
    }

    if (params.help()) {
      throw new UsageException();
    }

    Path path = Paths.get(params.file());
    String input;
    try {
      input = new String(Files.readAllBytes(path), UTF_8);
    } catch (IOException e) {
      errWriter.println(params.file() + ": could not read file: " + e.getMessage());
      return 1;
    }

    String fixed;
    try {
      fixed = Importer.addUsedImports(path, input);
    } catch (ImporterException e) {
      for (ImporterException.ImporterDiagnostic d : e.diagnostics()) {
        errWriter.println(d);
      }

      return 1;
    }

    outWriter.write(fixed);
    return 0;
  }
}
