package org.molgenis.app.gavin.meta;

import static org.molgenis.app.gavin.meta.GavinRunMetadata.DISCARDED_INPUT_FILE;
import static org.molgenis.app.gavin.meta.GavinRunMetadata.FILTERED_INPUT_FILE;
import static org.molgenis.app.gavin.meta.GavinRunMetadata.FINISHED_AT;
import static org.molgenis.app.gavin.meta.GavinRunMetadata.ID;
import static org.molgenis.app.gavin.meta.GavinRunMetadata.INPUT_FILE_NAME;
import static org.molgenis.app.gavin.meta.GavinRunMetadata.OUTPUT_FILE;
import static org.molgenis.app.gavin.meta.GavinRunMetadata.STARTED_AT;
import static org.molgenis.app.gavin.meta.GavinRunMetadata.STATUS;
import static org.molgenis.app.gavin.meta.GavinRunMetadata.SUBMITTED_AT;

import java.time.Instant;
import java.util.Optional;
import org.molgenis.data.Entity;
import org.molgenis.data.file.model.FileMeta;
import org.molgenis.data.meta.model.EntityType;
import org.molgenis.data.support.StaticEntity;
import org.molgenis.jobs.model.JobExecution.Status;

public class GavinRun extends StaticEntity {

  public GavinRun(Entity entity) {
    super(entity);
  }

  public GavinRun(EntityType entityType) {
    super(entityType);
  }

  public GavinRun(String id, EntityType entityType) {
    super(entityType);
    setId(id);
  }

  public void setId(String id) {
    set(ID, id);
  }

  public String getId() {
    return getString(ID);
  }

  public String getInputFileName() {
    return getString(GavinRunMetadata.INPUT_FILE_NAME);
  }

  public void setInputFileName(String inputFileName) {
    set(INPUT_FILE_NAME, inputFileName);
  }

  public Optional<FileMeta> getFilteredInputFile() {
    return Optional.ofNullable(getEntity(FILTERED_INPUT_FILE, FileMeta.class));
  }

  public void setFilteredInputFile(FileMeta filteredInputFile) {
    set(FILTERED_INPUT_FILE, filteredInputFile);
  }

  public Optional<FileMeta> getDiscardedInputFile() {
    return Optional.ofNullable(getEntity(DISCARDED_INPUT_FILE, FileMeta.class));
  }

  public void setDiscardedInputFile(FileMeta discardedInputFile) {
    set(DISCARDED_INPUT_FILE, discardedInputFile);
  }

  public Optional<FileMeta> getOutputFile() {
    return Optional.ofNullable(getEntity(OUTPUT_FILE, FileMeta.class));
  }

  public void setOutputFile(FileMeta outputFile) {
    set(OUTPUT_FILE, outputFile);
  }

  public Optional<String> getLog() {
    return Optional.ofNullable(getString(GavinRunMetadata.LOG));
  }

  public void setLog(String log) {
    set(GavinRunMetadata.LOG, log);
  }

  public Status getStatus() {
    return Status.valueOf(getString(STATUS));
  }

  public void setStatus(Status status) {
    set(STATUS, status.toString().toUpperCase());
  }

  public Instant getSubmittedAt() {
    return getInstant(SUBMITTED_AT);
  }

  public void setSubmittedAt(Instant dateTime) {
    set(SUBMITTED_AT, dateTime);
  }

  public Optional<Instant> getStartedAt() {
    return Optional.ofNullable(getInstant(STARTED_AT));
  }

  public void setStartedAt(Instant dateTime) {
    set(STARTED_AT, dateTime);
  }

  public Optional<Instant> getFinishedAt() {
    return Optional.ofNullable(getInstant(FINISHED_AT));
  }

  public void setFinishedAt(Instant dateTime) {
    set(FINISHED_AT, dateTime);
  }
}
