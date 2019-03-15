package org.molgenis.app.gavin;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import org.molgenis.app.gavin.meta.GavinRun;

public interface GavinService {
  String uploadVcfFile(HttpServletRequest httpServletRequest) throws IOException, ServletException;

  GavinRun get(String key);
}
