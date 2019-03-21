package org.molgenis.app.gavin;

import static java.lang.String.format;
import static org.molgenis.app.gavin.GavinController.GAVIN;

import com.google.auto.value.AutoValue;
import java.time.Instant;
import javax.annotation.Nullable;
import org.molgenis.app.gavin.meta.GavinRun;
import org.molgenis.jobs.model.JobExecution.Status;
import org.molgenis.util.AutoGson;

@AutoValue
@AutoGson(autoValueClass = AutoValue_GavinRunResponse.class)
public abstract class GavinRunResponse {
  public abstract String getId();

  public abstract String getUploadedFileName();

  public abstract String getFilteredInputFileUri();

  public abstract String getDiscardedInputFileUri();

  @Nullable
  public abstract String getOutputFileUri();

  public abstract Status getStatus();

  @Nullable
  public abstract String getLog();

  public abstract Instant getSubmittedAt();

  @Nullable
  public abstract Instant getStartedAt();

  @Nullable
  public abstract Instant getFinishedAt();

  static GavinRunResponse create(GavinRun gavinRun) {
    String outputUrl =
        gavinRun.getOutputFile().isPresent()
            ? format("%s/run/%s/download/output", GAVIN, gavinRun.getId())
            : null;

    return new AutoValue_GavinRunResponse(
        gavinRun.getId(),
        gavinRun.getInputFileName(),
        format("%s/run/%s/download/input", GAVIN, gavinRun.getId()),
        format("%s/run/%s/download/error", GAVIN, gavinRun.getId()),
        outputUrl,
        gavinRun.getStatus(),
        gavinRun.getLog().orElse(null),
        gavinRun.getSubmittedAt(),
        gavinRun.getStartedAt().orElse(null),
        gavinRun.getFinishedAt().orElse(null));
  }
}
