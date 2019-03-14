package org.molgenis.app.gavin.meta;

import static java.util.Objects.requireNonNull;
import static org.molgenis.data.meta.AttributeType.DATE_TIME;
import static org.molgenis.data.meta.AttributeType.ENUM;
import static org.molgenis.data.meta.AttributeType.FILE;
import static org.molgenis.data.meta.model.EntityType.AttributeRole.ROLE_ID;
import static org.molgenis.data.meta.model.Package.PACKAGE_SEPARATOR;
import static org.molgenis.jobs.model.JobExecutionMetaData.PENDING;

import org.molgenis.data.file.model.FileMetaMetaData;
import org.molgenis.data.meta.AttributeType;
import org.molgenis.data.meta.SystemEntityType;
import org.molgenis.jobs.model.JobExecution.Status;
import org.springframework.stereotype.Component;

@Component
public class GavinRunMetadata extends SystemEntityType {

  private static final String SIMPLE_NAME = "GavinRun";
  public static final String GAVIN_RUN =
      GavinPackage.PACKAGE_GAVIN + PACKAGE_SEPARATOR + SIMPLE_NAME;

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String INPUT_FILE_NAME = "inputFileName";
  public static final String FILTERED_INPUT_FILE = "filteredInputFile";
  public static final String DISCARDED_INPUT_FILE = "discardedInputFile";
  public static final String OUTPUT_FILE = "outputFile";
  public static final String LOG = "log";
  public static final String STATUS = "status";
  public static final String SUBMITTED_AT = "submittedAt";
  public static final String STARTED_AT = "startedAt";
  public static final String FINISHED_AT = "finishedAt";

  private final GavinPackage gavinPackage;
  private final FileMetaMetaData fileMetaMetadata;

  public GavinRunMetadata(GavinPackage gavinPackage, FileMetaMetaData fileMetaMetadata) {
    super(SIMPLE_NAME, GavinPackage.PACKAGE_GAVIN);
    this.gavinPackage = requireNonNull(gavinPackage);
    this.fileMetaMetadata = requireNonNull(fileMetaMetadata);
  }

  @Override
  public void init() {
    setPackage(gavinPackage);
    setLabel("GAVIN Run");
    setDescription("Stores input/output data and status information of GAVIN runs.");

    addAttribute(ID, ROLE_ID).setLabel("Identifier").setAuto(true);
    addAttribute(KEY).setLabel("Key").setUnique(true);
    addAttribute(INPUT_FILE_NAME).setNillable(false);
    addAttribute(FILTERED_INPUT_FILE)
        .setDataType(FILE)
        .setLabel("Filtered Input File")
        .setRefEntity(fileMetaMetadata);
    addAttribute(DISCARDED_INPUT_FILE)
        .setDataType(FILE)
        .setLabel("Discarded Input File")
        .setRefEntity(fileMetaMetadata);
    addAttribute(OUTPUT_FILE)
        .setDataType(FILE)
        .setLabel("Output File")
        .setRefEntity(fileMetaMetadata);
    addAttribute(LOG).setDataType(AttributeType.TEXT).setLabel("Log");
    addAttribute(SUBMITTED_AT).setDataType(DATE_TIME).setLabel("Submitted at").setNillable(false);
    addAttribute(STARTED_AT).setDataType(DATE_TIME).setLabel("Started at");
    addAttribute(FINISHED_AT).setDataType(DATE_TIME).setLabel("Finished at");
    addAttribute(STATUS)
        .setDataType(ENUM)
        .setEnumOptions(Status.class)
        .setLabel("Status")
        .setNillable(false)
        .setDefaultValue(PENDING);
  }
}
