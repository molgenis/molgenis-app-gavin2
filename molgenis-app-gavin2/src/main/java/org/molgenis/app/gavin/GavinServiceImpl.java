package org.molgenis.app.gavin;

import static java.util.Objects.requireNonNull;
import static org.molgenis.app.gavin.meta.GavinRunMetadata.GAVIN_RUN;
import static org.molgenis.data.file.model.FileMetaMetaData.FILE_META;

import com.google.common.collect.Multiset;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.stream.Stream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import org.molgenis.app.gavin.input.Parser;
import org.molgenis.app.gavin.input.model.LineType;
import org.molgenis.app.gavin.meta.GavinRun;
import org.molgenis.app.gavin.meta.GavinRunFactory;
import org.molgenis.core.ui.file.FileDownloadController;
import org.molgenis.data.DataService;
import org.molgenis.data.UnknownEntityException;
import org.molgenis.data.file.FileStore;
import org.molgenis.data.file.model.FileMeta;
import org.molgenis.data.file.model.FileMetaFactory;
import org.molgenis.data.populate.IdGenerator;
import org.molgenis.data.populate.IdGenerator.Strategy;
import org.molgenis.data.rest.service.ServletUriComponentsBuilderFactory;
import org.molgenis.jobs.model.JobExecution.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

@Component
public class GavinServiceImpl implements GavinService {

  private static final Logger LOG = LoggerFactory.getLogger(GavinServiceImpl.class);
  private final IdGenerator idGenerator;
  private final FileStore fileStore;
  private final FileMetaFactory fileMetaFactory;
  private final ServletUriComponentsBuilderFactory servletUriComponentsBuilderFactory;
  private final DataService dataService;
  private final GavinRunFactory gavinRunFactory;
  private final Parser parser;

  GavinServiceImpl(
      IdGenerator idGenerator,
      FileStore fileStore,
      FileMetaFactory fileMetaFactory,
      ServletUriComponentsBuilderFactory servletUriComponentsBuilderFactory,
      DataService dataService,
      GavinRunFactory gavinRunFactory,
      Parser parser) {
    this.idGenerator = requireNonNull(idGenerator);
    this.fileStore = requireNonNull(fileStore);
    this.fileMetaFactory = requireNonNull(fileMetaFactory);
    this.servletUriComponentsBuilderFactory = requireNonNull(servletUriComponentsBuilderFactory);
    this.dataService = requireNonNull(dataService);
    this.gavinRunFactory = requireNonNull(gavinRunFactory);
    this.parser = requireNonNull(parser);
  }

  /**
   * Saves the uploaded file, parses it and creates a GavinRun.
   *
   * <p>Requires the HttpServletRequest to contain a multipart form with a file named "file".
   */
  @Override
  @Transactional
  public String upload(HttpServletRequest httpServletRequest) throws IOException {
    Part part;
    try {
      part = httpServletRequest.getPart("file");
    } catch (ServletException e) {
      throw new IllegalStateException("Request is not of type multipart/form-data");
    }

    FileMeta inputFile = storeUploadedFile(part);
    FileMeta filteredInput = createEmptyFile("filteredInput.vcf");
    FileMeta discardedInput = createEmptyFile("discardedInput.txt");

    GavinRun gavinRun = createGavinRun(part, filteredInput, discardedInput);
    LOG.info("GavinRun created: '{}'", gavinRun.getId());

    Multiset<LineType> parsedLineTypes = parseInputFile(inputFile, filteredInput, discardedInput);
    if (parsedLineTypes.count(LineType.VCF) == 0) {
      fail(gavinRun.getId(), "No usable lines were found in the uploaded file");
    }

    dataService.delete(FILE_META, inputFile);

    return gavinRun.getId();
  }

  private Multiset<LineType> parseInputFile(
      FileMeta inputFile, FileMeta filteredInput, FileMeta discardedInput) throws IOException {
    Multiset<LineType> parsedLineTypes =
        parser.tryTransform(
            fileStore.getFile(inputFile.getId()),
            fileStore.getFile(filteredInput.getId()),
            fileStore.getFile(discardedInput.getId()));

    filteredInput.setSize(fileStore.getFile(filteredInput.getId()).length());
    discardedInput.setSize(fileStore.getFile(discardedInput.getId()).length());
    dataService.update(FILE_META, Stream.of(filteredInput, discardedInput));
    return parsedLineTypes;
  }

  private GavinRun createGavinRun(Part part, FileMeta filteredInput, FileMeta discardedInput) {
    GavinRun gavinRun = gavinRunFactory.create();
    gavinRun.setId(idGenerator.generateId(Strategy.LONG_SECURE_RANDOM));
    gavinRun.setInputFileName(part.getSubmittedFileName());
    gavinRun.setFilteredInputFile(filteredInput);
    gavinRun.setDiscardedInputFile(discardedInput);
    gavinRun.setSubmittedAt(Instant.now());
    gavinRun.setStatus(Status.PENDING);
    dataService.add(GAVIN_RUN, gavinRun);
    return gavinRun;
  }

  @Override
  public GavinRun get(String id) {
    LOG.debug("Getting GavinRun '{}'", id);

    GavinRun gavinRun = dataService.findOneById(GAVIN_RUN, id, GavinRun.class);
    if (gavinRun == null) {
      throw new UnknownEntityException(GAVIN_RUN, id);
    }
    return gavinRun;
  }

  @Override
  @Transactional
  public void start(String id) {
    LOG.info("GavinRun has started: '{}'", id);

    GavinRun gavinRun = get(id);
    gavinRun.setStartedAt(Instant.now());
    gavinRun.setStatus(Status.RUNNING);
    dataService.update(GAVIN_RUN, gavinRun);
  }

  @Override
  @Transactional
  public void finish(String id, String log, HttpServletRequest httpServletRequest)
      throws IOException {
    LOG.info("GavinRun has finished: '{}'", id);

    GavinRun gavinRun = get(id);
    FileMeta outputFile;
    try {
      outputFile = storeUploadedFile(httpServletRequest.getPart("outputFile"));
    } catch (ServletException e) {
      throw new IllegalStateException("Request is not of type multipart/form-data");
    }

    gavinRun.setOutputFile(outputFile);
    gavinRun.setLog(gavinRun.getLog().orElse("") + log);
    gavinRun.setFinishedAt(Instant.now());
    gavinRun.setStatus(Status.SUCCESS);
    dataService.update(GAVIN_RUN, gavinRun);
  }

  @Override
  @Transactional
  public void fail(String id, String log) {
    LOG.info("GavinRun failed: '{}'", id);

    GavinRun gavinRun = get(id);
    gavinRun.setLog(gavinRun.getLog().orElse("") + log);
    gavinRun.setFinishedAt(Instant.now());
    gavinRun.setStatus(Status.FAILED);
    dataService.update(GAVIN_RUN, gavinRun);
  }

  private FileMeta createEmptyFile(String fileName) {
    String id = idGenerator.generateId();
    try (InputStream inputStream = new ByteArrayInputStream(new byte[] {})) {
      fileStore.store(inputStream, id);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    return createFileMeta(id, fileName, "text/tsv");
  }

  private FileMeta storeUploadedFile(Part part) {
    String id = idGenerator.generateId();
    try (InputStream inputStream = part.getInputStream()) {
      fileStore.store(inputStream, id);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    return createFileMeta(id, part.getSubmittedFileName(), part.getContentType());
  }

  private FileMeta createFileMeta(String id, String fileName, String contentType) {
    FileMeta fileEntity = fileMetaFactory.create(id);
    fileEntity.setFilename(fileName);
    fileEntity.setContentType(contentType);
    fileEntity.setSize(fileStore.getFile(id).length());
    ServletUriComponentsBuilder currentRequest =
        servletUriComponentsBuilderFactory.fromCurrentRequest();
    UriComponents downloadUri =
        currentRequest
            .replacePath(FileDownloadController.URI + '/' + id)
            .replaceQuery(null)
            .build();
    fileEntity.setUrl(downloadUri.toUriString());
    dataService.add(FILE_META, fileEntity);
    return fileEntity;
  }
}
