package org.molgenis.app.gavin;

import static java.util.Objects.requireNonNull;
import static org.molgenis.app.gavin.meta.GavinRunMetadata.GAVIN_RUN;
import static org.molgenis.data.file.model.FileMetaMetaData.FILE_META;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.stream.Stream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import org.molgenis.app.gavin.input.Parser;
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
@SuppressWarnings({"squid:S1854", "squid:S1481", "squid:S3958"}) // TODO REMOVE ME
public class GavinServiceImpl implements GavinService {

  private static final Logger LOG = LoggerFactory.getLogger(GavinServiceImpl.class);
  private final IdGenerator idGenerator;
  private final FileStore fileStore;
  private final FileMetaFactory fileMetaFactory;
  private final ServletUriComponentsBuilderFactory servletUriComponentsBuilderFactory;
  private final DataService dataService;
  private final GavinRunFactory gavinRunFactory;
  private final Parser parser;

  public GavinServiceImpl(
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

  @Override
  @Transactional
  public String upload(HttpServletRequest httpServletRequest) throws IOException, ServletException {
    Part part = httpServletRequest.getPart("file");

    FileMeta inputFile = storeUploadedFile(part);
    FileMeta filteredInput = createEmptyFile("filteredInput.vcf");
    FileMeta discardedInput = createEmptyFile("discardedInput.txt");

    GavinRun gavinRun = gavinRunFactory.create();
    gavinRun.setId(idGenerator.generateId(Strategy.LONG_SECURE_RANDOM));
    gavinRun.setInputFileName(part.getSubmittedFileName());
    gavinRun.setFilteredInputFile(filteredInput);
    gavinRun.setDiscardedInputFile(discardedInput);
    gavinRun.setSubmittedAt(Instant.now());
    gavinRun.setStatus(Status.PENDING);
    dataService.add(GAVIN_RUN, gavinRun);

    parser.tryTransform(
        fileStore.getFile(inputFile.getId()),
        fileStore.getFile(filteredInput.getId()),
        fileStore.getFile(discardedInput.getId()));

    File filteredInputFile = fileStore.getFile(filteredInput.getId());

    filteredInput.setSize(filteredInputFile.length());
    discardedInput.setSize(fileStore.getFile(discardedInput.getId()).length());
    dataService.update(FILE_META, Stream.of(filteredInput, discardedInput));

    return gavinRun.getId();
  }

  @Override
  public GavinRun get(String id) {
    GavinRun gavinRun = dataService.findOneById(GAVIN_RUN, id, GavinRun.class);
    if (gavinRun == null) {
      throw new UnknownEntityException(GAVIN_RUN, id);
    }
    return gavinRun;
  }

  @Override
  public void start(String id) {
    GavinRun gavinRun = get(id);
    gavinRun.setStartedAt(Instant.now());
    gavinRun.setStatus(Status.RUNNING);
    dataService.update(GAVIN_RUN, gavinRun);
  }

  @Override
  public void finish(String id, String log, HttpServletRequest httpServletRequest)
      throws IOException, ServletException {
    GavinRun gavinRun = get(id);

    FileMeta outputFile = storeUploadedFile(httpServletRequest.getPart("outputFile"));

    gavinRun.setOutputFile(outputFile);
    gavinRun.setLog(gavinRun.getLog() + log);
    gavinRun.setFinishedAt(Instant.now());
    gavinRun.setStatus(Status.SUCCESS);
    dataService.update(GAVIN_RUN, gavinRun);
  }

  @Override
  public void fail(String id, String log) {
    GavinRun gavinRun = get(id);

    gavinRun.setLog(gavinRun.getLog() + log);
    gavinRun.setFinishedAt(Instant.now());
    gavinRun.setStatus(Status.FAILED);
    dataService.update(GAVIN_RUN, gavinRun);
  }

  private FileMeta createEmptyFile(String fileName) {
    String id = idGenerator.generateId();
    try (InputStream inputStream = new ByteArrayInputStream("".getBytes())) {
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
