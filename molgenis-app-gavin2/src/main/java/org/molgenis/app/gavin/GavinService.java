package org.molgenis.app.gavin;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import org.molgenis.app.gavin.meta.GavinRun;

public interface GavinService {
  String upload(HttpServletRequest httpServletRequest) throws IOException;

  GavinRun get(String id);

  void start(String id);

  void finish(String id, String log, HttpServletRequest httpServletRequest) throws IOException;

  void fail(String id, String log);
}
