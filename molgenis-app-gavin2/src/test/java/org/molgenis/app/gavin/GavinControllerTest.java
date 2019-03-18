package org.molgenis.app.gavin;

import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.molgenis.app.gavin.meta.GavinRunMetadata.GAVIN_RUN;
import static org.molgenis.data.file.model.FileMetaMetaData.FILE_META;
import static org.molgenis.data.importer.ImportRunMetaData.STATUS;
import static org.testng.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.stream.Stream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.mockito.Answers;
import org.mockito.Mock;
import org.molgenis.app.gavin.meta.GavinRun;
import org.molgenis.data.DataService;
import org.molgenis.data.file.FileStore;
import org.molgenis.data.file.model.FileMeta;
import org.molgenis.jobs.model.JobExecution.Status;
import org.molgenis.test.AbstractMockitoTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class GavinControllerTest extends AbstractMockitoTest {

  private GavinController controller;

  @Mock private GavinService gavinService;
  @Mock private FileStore fileStore;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private DataService dataService;

  @BeforeMethod
  public void beforeMethod() {
    controller = new GavinController(gavinService, fileStore, dataService);
  }

  @Test
  public void testUpload() throws IOException, ServletException {
    MultipartFile multipartFile = mock(MultipartFile.class);
    HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
    when(gavinService.upload(httpServletRequest)).thenReturn("id");

    ResponseEntity response = controller.upload(multipartFile, httpServletRequest);

    verify(gavinService).upload(httpServletRequest);
    assertEquals(response.getStatusCode(), HttpStatus.CREATED);
    assertEquals(response.getBody(), "id");
  }

  @Test
  public void testGet() {
    GavinRun gavinRun = mock(GavinRun.class);
    when(gavinRun.getId()).thenReturn("id");
    when(gavinRun.getInputFileName()).thenReturn("file");
    when(gavinRun.getStatus()).thenReturn(Status.PENDING);
    when(gavinRun.getSubmittedAt()).thenReturn(Instant.now());
    when(gavinService.get("id")).thenReturn(gavinRun);

    controller.get("id");

    verify(gavinService).get("id");
  }

  @Test
  public void testStart() {
    ResponseEntity response = controller.start("id");

    verify(gavinService).start("id");
    assertEquals(response.getStatusCode(), HttpStatus.OK);
  }

  @Test
  public void testFinish() throws IOException, ServletException {
    MultipartFile outputFile = mock(MultipartFile.class);
    HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

    ResponseEntity response =
        controller.finish("id", outputFile, "All went well.", httpServletRequest);

    verify(gavinService).finish("id", "All went well.", httpServletRequest);
    assertEquals(response.getStatusCode(), HttpStatus.OK);
  }

  @Test
  public void testDownloadOutputFile() {
    GavinRun gavinRun = mock(GavinRun.class);
    FileMeta fileMeta = mock(FileMeta.class);
    when(gavinRun.getOutputFile()).thenReturn(fileMeta);
    when(fileMeta.getId()).thenReturn("fileId");
    when(fileMeta.getFilename()).thenReturn("fileName");
    HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);
    when(gavinService.get("id")).thenReturn(gavinRun);
    File file = mock(File.class);
    when(fileStore.getFile("fileId")).thenReturn(file);

    FileSystemResource resource = controller.downloadOutputFile(httpServletResponse, "id");

    verify(gavinService).get("id");
    verify(fileStore).getFile("fileId");
    assertEquals(resource.getFile(), file);
  }

  @Test
  public void testDownloadInputFile() {
    GavinRun gavinRun = mock(GavinRun.class);
    FileMeta fileMeta = mock(FileMeta.class);
    when(gavinRun.getFilteredInputFile()).thenReturn(fileMeta);
    when(fileMeta.getId()).thenReturn("fileId");
    when(fileMeta.getFilename()).thenReturn("fileName");
    HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);
    when(gavinService.get("id")).thenReturn(gavinRun);
    File file = mock(File.class);
    when(fileStore.getFile("fileId")).thenReturn(file);

    FileSystemResource resource = controller.downloadInputFile(httpServletResponse, "id");

    verify(gavinService).get("id");
    verify(fileStore).getFile("fileId");
    assertEquals(resource.getFile(), file);
  }

  @Test
  public void testDownloadErrorFile() {
    GavinRun gavinRun = mock(GavinRun.class);
    FileMeta fileMeta = mock(FileMeta.class);
    when(gavinRun.getDiscardedInputFile()).thenReturn(fileMeta);
    when(fileMeta.getId()).thenReturn("fileId");
    when(fileMeta.getFilename()).thenReturn("fileName");
    HttpServletResponse httpServletResponse = mock(HttpServletResponse.class);
    when(gavinService.get("id")).thenReturn(gavinRun);
    File file = mock(File.class);
    when(fileStore.getFile("fileId")).thenReturn(file);

    FileSystemResource resource = controller.downloadErrorFile(httpServletResponse, "id");

    verify(gavinService).get("id");
    verify(fileStore).getFile("fileId");
    assertEquals(resource.getFile(), file);
  }

  @Test
  public void testCleanUp() {
    GavinRun gavinRun = mock(GavinRun.class);
    GavinRun expiredGavinRun = mock(GavinRun.class);
    GavinRun expiredGavinRunWithoutFiles = mock(GavinRun.class);

    when(gavinRun.getFinishedAt()).thenReturn(Instant.now());
    when(expiredGavinRun.getFinishedAt()).thenReturn(Instant.ofEpochSecond(0));
    when(expiredGavinRunWithoutFiles.getFinishedAt()).thenReturn(Instant.ofEpochSecond(0));

    FileMeta filteredInputFileMeta = mock(FileMeta.class);
    FileMeta discardedInputFileMeta = mock(FileMeta.class);
    FileMeta outputFileMeta = mock(FileMeta.class);
    when(expiredGavinRun.getFilteredInputFile()).thenReturn(filteredInputFileMeta);
    when(expiredGavinRun.getDiscardedInputFile()).thenReturn(discardedInputFileMeta);
    when(expiredGavinRun.getOutputFile()).thenReturn(outputFileMeta);

    when(dataService
            .query(GAVIN_RUN, GavinRun.class)
            .in(STATUS, asList(Status.FAILED, Status.SUCCESS))
            .findAll())
        .thenReturn(Stream.of(gavinRun, expiredGavinRun, expiredGavinRunWithoutFiles));

    controller.cleanUp();

    verify(dataService).delete(FILE_META, filteredInputFileMeta);
    verify(dataService).delete(FILE_META, discardedInputFileMeta);
    verify(dataService).delete(FILE_META, outputFileMeta);
    verify(gavinRun, never()).getFilteredInputFile();
    verify(gavinRun, never()).getDiscardedInputFile();
    verify(gavinRun, never()).getOutputFile();
    verify(expiredGavinRunWithoutFiles, never()).setFilteredInputFile(null);
    verify(expiredGavinRunWithoutFiles, never()).setDiscardedInputFile(null);
    verify(expiredGavinRunWithoutFiles, never()).setOutputFile(null);
  }
}
