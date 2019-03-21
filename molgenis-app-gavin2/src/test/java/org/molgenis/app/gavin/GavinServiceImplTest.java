package org.molgenis.app.gavin;

import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.molgenis.app.gavin.meta.GavinRunMetadata.GAVIN_RUN;
import static org.molgenis.data.file.model.FileMetaMetaData.FILE_META;
import static org.testng.Assert.assertEquals;

import com.google.common.collect.EnumMultiset;
import com.google.common.collect.Multiset;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.molgenis.app.gavin.input.Parser;
import org.molgenis.app.gavin.input.model.LineType;
import org.molgenis.app.gavin.meta.GavinRun;
import org.molgenis.app.gavin.meta.GavinRunFactory;
import org.molgenis.data.DataService;
import org.molgenis.data.UnknownEntityException;
import org.molgenis.data.file.FileStore;
import org.molgenis.data.file.model.FileMeta;
import org.molgenis.data.file.model.FileMetaFactory;
import org.molgenis.data.populate.IdGenerator;
import org.molgenis.data.rest.service.ServletUriComponentsBuilderFactory;
import org.molgenis.jobs.model.JobExecution.Status;
import org.molgenis.test.AbstractMockitoTest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class GavinServiceImplTest extends AbstractMockitoTest {

  private GavinService gavinService;

  @Mock private IdGenerator idGenerator;
  @Mock private FileStore fileStore;
  @Mock private FileMetaFactory fileMetaFactory;
  @Mock private ServletUriComponentsBuilderFactory servletUriComponentsBuilderFactory;
  @Mock private DataService dataService;
  @Mock private GavinRunFactory gavinRunFactory;
  @Mock private Parser parser;

  @BeforeMethod
  public void beforeMethod() {
    gavinService =
        new GavinServiceImpl(
            idGenerator,
            fileStore,
            fileMetaFactory,
            servletUriComponentsBuilderFactory,
            dataService,
            gavinRunFactory,
            parser);
  }

  @Test
  public void testUpload() throws IOException, ServletException {
    mockComponentBuilder();
    String inputFileId = "inputFileId";
    String filteredInputFileId = "filteredInputFileId";
    String discardedInputFileId = "discardedInputFileId";
    setupIdGeneratorInOrder(inputFileId, filteredInputFileId, discardedInputFileId, "gavinRunId");

    File inputFile = mockFile();
    File filteredInputFile = mockFile();
    File discardedInputFile = mockFile();
    Map<String, File> files = new HashMap<>();
    files.put(inputFileId, inputFile);
    files.put(filteredInputFileId, filteredInputFile);
    files.put(discardedInputFileId, discardedInputFile);
    setupFileStore(files);

    FileMeta inputFileMeta = mockFileMeta(inputFileId);
    FileMeta filteredInputFileMeta = mockFileMeta(filteredInputFileId);
    FileMeta discardedInputFileMeta = mockFileMeta(discardedInputFileId);
    Map<String, FileMeta> fileMetas = new HashMap<>();
    fileMetas.put(inputFileId, inputFileMeta);
    fileMetas.put(filteredInputFileId, filteredInputFileMeta);
    fileMetas.put(discardedInputFileId, discardedInputFileMeta);
    setupFileMetaFactory(fileMetas);

    HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
    Part part = mock(Part.class);
    when(httpServletRequest.getPart("file")).thenReturn(part);
    GavinRun gavinRun = mock(GavinRun.class);
    when(gavinRunFactory.create()).thenReturn(gavinRun);

    Multiset<LineType> parsedLineTypes = EnumMultiset.create(LineType.class);
    parsedLineTypes.add(LineType.VCF, 1);
    when(parser.tryTransform(inputFile, filteredInputFile, discardedInputFile))
        .thenReturn(parsedLineTypes);

    gavinService.upload(httpServletRequest);

    verify(dataService).add(GAVIN_RUN, gavinRun);
    verify(dataService).delete(FILE_META, inputFileMeta);
  }

  @Test
  public void testGet() {
    String id = "test";
    GavinRun gavinRun = mock(GavinRun.class);
    when(dataService.findOneById(GAVIN_RUN, id, GavinRun.class)).thenReturn(gavinRun);

    assertEquals(gavinService.get(id), gavinRun);
  }

  @Test(expectedExceptions = UnknownEntityException.class)
  public void testGetNotExists() {
    gavinService.get("test");
  }

  @Test
  public void testStart() {
    String id = "test";
    GavinRun gavinRun = mock(GavinRun.class);
    when(dataService.findOneById(GAVIN_RUN, id, GavinRun.class)).thenReturn(gavinRun);

    gavinService.start(id);

    verify(gavinRun).setStartedAt(any(Instant.class));
    verify(gavinRun).setStatus(Status.RUNNING);
    verify(dataService).update(GAVIN_RUN, gavinRun);
  }

  @Test
  public void testFinish() throws IOException, ServletException {
    mockComponentBuilder();
    String id = "test";
    GavinRun gavinRun = mock(GavinRun.class);
    when(dataService.findOneById(GAVIN_RUN, id, GavinRun.class)).thenReturn(gavinRun);
    HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
    Part part = mock(Part.class);
    when(httpServletRequest.getPart("outputFile")).thenReturn(part);
    FileMeta outputFileMeta = mock(FileMeta.class);
    when(fileMetaFactory.create(any(String.class))).thenReturn(outputFileMeta);
    when(idGenerator.generateId()).thenReturn("fileId");
    File outputFile = mockFile();
    when(fileStore.getFile("fileId")).thenReturn(outputFile);

    gavinService.finish(id, "Great success!", httpServletRequest);

    verify(dataService).add(FILE_META, outputFileMeta);
    verify(gavinRun).setOutputFile(outputFileMeta);
    verify(gavinRun).setLog("Great success!");
    verify(gavinRun).setFinishedAt(any(Instant.class));
    verify(gavinRun).setStatus(Status.SUCCESS);
    verify(dataService).update(GAVIN_RUN, gavinRun);
  }

  @Test
  public void testFail() {
    String id = "test";
    GavinRun gavinRun = mock(GavinRun.class);
    when(dataService.findOneById(GAVIN_RUN, id, GavinRun.class)).thenReturn(gavinRun);

    gavinService.fail(id, "Failed because of x");

    verify(gavinRun).setLog("Failed because of x");
    verify(gavinRun).setFinishedAt(any(Instant.class));
    verify(gavinRun).setStatus(Status.FAILED);
    verify(dataService).update(GAVIN_RUN, gavinRun);
  }

  private void mockComponentBuilder() {
    ServletUriComponentsBuilder mockBuilder = mock(ServletUriComponentsBuilder.class);
    UriComponents downloadUri = mock(UriComponents.class);
    when(mockBuilder.replacePath(anyString())).thenReturn(mockBuilder);
    when(mockBuilder.replaceQuery(null)).thenReturn(mockBuilder);
    when(downloadUri.toUriString()).thenReturn("http://somedownloaduri");
    when(mockBuilder.build()).thenReturn(downloadUri);
    when(servletUriComponentsBuilderFactory.fromCurrentRequest()).thenReturn(mockBuilder);
  }

  private void setupIdGeneratorInOrder(String... ids) {
    when(idGenerator.generateId())
        .thenAnswer(
            new Answer() {
              private Iterator<String> idsIterator = asList(ids).iterator();

              @Override
              public Object answer(InvocationOnMock invocation) {
                return idsIterator.next();
              }
            });
  }

  private void setupFileStore(Map<String, File> files) {
    when(fileStore.getFile(any(String.class)))
        .thenAnswer(
            (Answer)
                invocation -> {
                  String id = invocation.getArgument(0);
                  return files.get(id);
                });
  }

  private void setupFileMetaFactory(Map<String, FileMeta> fileMetas) {
    when(fileMetaFactory.create(any(String.class)))
        .thenAnswer(
            (Answer)
                invocation -> {
                  String id = invocation.getArgument(0);
                  return fileMetas.get(id);
                });
  }

  private static File mockFile() {
    File file = mock(File.class);
    when(file.length()).thenReturn(1L);
    return file;
  }

  private FileMeta mockFileMeta(String id) {
    FileMeta fileMeta = mock(FileMeta.class);
    when(fileMeta.getId()).thenReturn(id);
    return fileMeta;
  }
}
