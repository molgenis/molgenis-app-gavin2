package org.molgenis.app.gavin.input;

import static org.slf4j.LoggerFactory.getLogger;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.slf4j.Logger;

/** Writes lines to file. */
public class LineSink implements Consumer<String>, Closeable {
  private static final Logger LOG = getLogger(LineSink.class);
  private final File file;
  private BufferedWriter writer;

  LineSink(File file) {
    this.file = file;
  }

  @Override
  public void accept(String line) {
    try {
      if (writer == null) {
        writer = createWriter(file);
      }
      writer.write(line);
      writer.newLine();
    } catch (IOException ex) {
      LOG.error("Failed to write line to file {}.", file.getAbsolutePath(), ex);
    }
  }

  private BufferedWriter createWriter(File file) throws IOException {
    LOG.debug("Creating LineSink for {}.", file.getAbsolutePath());
    return new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
  }

  @Override
  public void close() throws IOException {
    if (writer != null) {
      LOG.debug("Closing LineSink for {}.", file.getAbsolutePath());
      writer.close();
    }
  }
}
