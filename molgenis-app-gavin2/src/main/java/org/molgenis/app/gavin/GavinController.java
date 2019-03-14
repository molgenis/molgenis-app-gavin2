package org.molgenis.app.gavin;

import static java.util.Objects.requireNonNull;
import static org.molgenis.app.gavin.GavinController.URI;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(URI)
public class GavinController {
  private static final String GAVIN = "api/gavin";
  static final String URI = GAVIN;
  private final GavinService gavinService;

  public GavinController(GavinService gavinService) {
    this.gavinService = requireNonNull(gavinService);
  }

  @PostMapping(value = "/upload")
  public ResponseEntity<String> upload(
      @RequestParam(value = "file") MultipartFile inputFile, HttpServletRequest httpServletRequest)
      throws IOException, ServletException {
    String key = gavinService.uploadVcfFile(httpServletRequest);
    return ResponseEntity.created(java.net.URI.create(key)).body(key);
  }
}
