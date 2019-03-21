package org.molgenis.app.gavin;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.molgenis.app.gavin.GavinController.URI;
import static org.molgenis.app.gavin.meta.GavinRunMetadata.GAVIN_RUN;
import static org.molgenis.data.file.model.FileMetaMetaData.FILE_META;
import static org.molgenis.data.importer.ImportRunMetaData.STATUS;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.molgenis.app.gavin.meta.GavinRun;
import org.molgenis.data.DataService;
import org.molgenis.data.file.FileStore;
import org.molgenis.data.file.model.FileMeta;
import org.molgenis.jobs.model.JobExecution.Status;
import org.molgenis.security.core.runas.RunAsSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
  private final DataService dataService;

  private static final Duration RUN_EXPIRATION_TIME = Duration.ofDays(1);

  GavinController(GavinService gavinService, FileStore fileStore, DataService dataService) {
    this.gavinService = requireNonNull(gavinService);
    this.fileStore = requireNonNull(fileStore);
    this.dataService = requireNonNull(dataService);
  }

  @RunAsSystem
  @SuppressWarnings("unused")
  @PostMapping(value = "/upload")
  public ResponseEntity<String> upload(
      @RequestParam(value = "file") MultipartFile inputFile, HttpServletRequest httpServletRequest)
      throws IOException {
    String id = gavinService.upload(httpServletRequest);
    return ResponseEntity.created(java.net.URI.create(id)).body(id);
  }

  @RunAsSystem
  @GetMapping(value = "/run/{id}")
  public GavinRunResponse get(@PathVariable String id) {
    return GavinRunResponse.create(gavinService.get(id));
  }

  @PostMapping(value = "/run/{id}/start")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void start(@PathVariable String id) {
    gavinService.start(id);
  }

  @SuppressWarnings("unused")
  @PostMapping(value = "/run/{id}/finish")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void finish(
      @PathVariable String id,
      @RequestParam MultipartFile outputFile,
      @RequestParam String log,
      HttpServletRequest httpServletRequest)
      throws IOException {
    gavinService.finish(id, log, httpServletRequest);
  }

  @PostMapping(value = "/run/{id}/fail")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void fail(@PathVariable String id, @RequestParam String log) {
    gavinService.fail(id, log);
  }

  @RunAsSystem
  @GetMapping(value = "/run/{id}/download/output", produces = APPLICATION_OCTET_STREAM_VALUE)
  public FileSystemResource downloadOutputFile(
      HttpServletResponse response, @PathVariable(value = "id") String id) {
    Optional<FileMeta> outputFile = gavinService.get(id).getOutputFile();
    return prepareDownload(response, outputFile.orElse(null));
  }

  @RunAsSystem
  @GetMapping(value = "/run/{id}/download/input", produces = APPLICATION_OCTET_STREAM_VALUE)
  public FileSystemResource downloadInputFile(
      HttpServletResponse response, @PathVariable(value = "id") String id) {
    Optional<FileMeta> inputFile = gavinService.get(id).getFilteredInputFile();
    return prepareDownload(response, inputFile.orElse(null));
  }

  @RunAsSystem
  @GetMapping(value = "/run/{id}/download/error", produces = APPLICATION_OCTET_STREAM_VALUE)
  public FileSystemResource downloadErrorFile(
      HttpServletResponse response, @PathVariable(value = "id") String id) {
    Optional<FileMeta> errorFile = gavinService.get(id).getDiscardedInputFile();
    return prepareDownload(response, errorFile.orElse(null));
  }

  private FileSystemResource prepareDownload(HttpServletResponse response, FileMeta fileMeta) {
    if (fileMeta == null) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return null;
    }

    File file = fileStore.getFile(fileMeta.getId());
    response.setHeader(
        "Content-Disposition", format("attachment; filename=\"%s\"", fileMeta.getFilename()));
    return new FileSystemResource(file);
  }

  /**
   * Removes files from gavin runs that have finished more than 24 hours ago. Does not remove the
   * GavinRun entities themselves so that they can be used for usage statistics.
   */
  @RunAsSystem
  @Transactional
  @Scheduled(fixedRate = 5 * 60 * 1000)
  public void cleanUp() {
    LOG.debug("Starting cleanup routine of expired GavinRuns");

    Stream<GavinRun> completedGavinRuns =
        dataService
            .query(GAVIN_RUN, GavinRun.class)
            .in(STATUS, asList(Status.FAILED, Status.SUCCESS))
            .findAll();

    completedGavinRuns
        .filter(this::hasExpired)
        .filter(this::containsFiles)
        .forEach(this::deleteFilesFromRun);

    LOG.debug("Cleanup routine ended");
  }

  private boolean containsFiles(GavinRun gavinRun) {
    return gavinRun.getFilteredInputFile().isPresent()
        || gavinRun.getDiscardedInputFile().isPresent()
        || gavinRun.getOutputFile().isPresent();
  }

  private boolean hasExpired(GavinRun gavinRun) {
    Optional<Instant> finishedAt = gavinRun.getFinishedAt();
    if (!finishedAt.isPresent()) {
      LOG.warn("GavinRun '{}' 'finishedAt' field is null. Marking as expired.", gavinRun.getId());
      return true;
    } else {
      Duration age = Duration.between(finishedAt.get(), Instant.now());
      return age.compareTo(RUN_EXPIRATION_TIME) > 0;
    }
  }

  private void deleteFilesFromRun(GavinRun gavinRun) {
    LOG.info("Deleting files of expired GavinRun '{}'", gavinRun.getId());

    Optional<FileMeta> filteredInput = gavinRun.getFilteredInputFile();
    Optional<FileMeta> discardedInput = gavinRun.getDiscardedInputFile();
    Optional<FileMeta> output = gavinRun.getOutputFile();

    gavinRun.setFilteredInputFile(null);
    gavinRun.setDiscardedInputFile(null);
    gavinRun.setOutputFile(null);
    dataService.update(GAVIN_RUN, gavinRun);

    deleteFile(filteredInput.orElse(null));
    deleteFile(discardedInput.orElse(null));
    deleteFile(output.orElse(null));

    LOG.info("Done deleting files of GavinRun '{}'", gavinRun.getId());
  }

  private void deleteFile(FileMeta fileMeta) {
    if (fileMeta != null) {
      dataService.delete(FILE_META, fileMeta);
    }
  }
}
