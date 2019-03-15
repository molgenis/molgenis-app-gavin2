package org.molgenis.app.gavin;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.molgenis.app.gavin.GavinController.URI;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

import java.io.File;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.molgenis.data.MolgenisDataException;
import org.molgenis.data.file.FileStore;
import org.molgenis.data.file.model.FileMeta;
import org.molgenis.security.core.runas.RunAsSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(URI)
public class GavinController {
  private static final Logger LOG = LoggerFactory.getLogger(GavinController.class);

  static final String GAVIN = "/api/gavin";
  static final String URI = GAVIN;
  private final GavinService gavinService;
  private final FileStore fileStore;

  public GavinController(GavinService gavinService, FileStore fileStore) {
    this.gavinService = requireNonNull(gavinService);
    this.fileStore = requireNonNull(fileStore);
  }

  @RunAsSystem
  @PostMapping(value = "/upload")
  public ResponseEntity<String> upload(
      @RequestParam(value = "file") MultipartFile inputFile, HttpServletRequest httpServletRequest)
      throws IOException, ServletException {
    String key = gavinService.uploadVcfFile(httpServletRequest);
    return ResponseEntity.created(java.net.URI.create(key)).body(key);
  }

  @RunAsSystem
  @GetMapping(value = "/run/{id}")
  public GavinRunResponse get(@PathVariable String id) {
    return GavinRunResponse.create(gavinService.get(id));
  }

  @RunAsSystem
  @GetMapping(value = "/run/{id}/download/output", produces = APPLICATION_OCTET_STREAM_VALUE)
  public FileSystemResource downloadOutputFile(
      HttpServletResponse response, @PathVariable(value = "id") String id) {
    FileMeta outputFile = gavinService.get(id).getOutputFile();
    return prepareDownload(response, outputFile);
  }

  @RunAsSystem
  @GetMapping(value = "/run/{id}/download/input", produces = APPLICATION_OCTET_STREAM_VALUE)
  public FileSystemResource downloadInputFile(
      HttpServletResponse response, @PathVariable(value = "id") String id) {
    FileMeta errorFile = gavinService.get(id).getFilteredInputFile();
    return prepareDownload(response, errorFile);
  }

  @RunAsSystem
  @GetMapping(value = "/run/{id}/download/error", produces = APPLICATION_OCTET_STREAM_VALUE)
  public FileSystemResource downloadErrorFile(
      HttpServletResponse response, @PathVariable(value = "id") String id) {
    FileMeta errorFile = gavinService.get(id).getDiscardedInputFile();
    return prepareDownload(response, errorFile);
  }

  private FileSystemResource prepareDownload(HttpServletResponse response, FileMeta fileMeta) {
    if (fileMeta == null) {
      throw new MolgenisDataException(
          "No result file found for this job. The run might not have finished yet or it might"
              + "be older than 24 hours.");
    }

    File file = fileStore.getFile(fileMeta.getId());

    response.setHeader(
        "Content-Disposition", format("inline; filename=\"%s-gavin.vcf\"", fileMeta.getFilename()));
    return new FileSystemResource(file);
  }
}
