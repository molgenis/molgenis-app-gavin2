package org.molgenis.app.gavin;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

public interface GavinService {
  String uploadVcfFile(HttpServletRequest httpServletRequest) throws IOException, ServletException;
}
