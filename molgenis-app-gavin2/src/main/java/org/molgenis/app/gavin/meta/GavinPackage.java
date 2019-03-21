package org.molgenis.app.gavin.meta;

import static java.util.Objects.requireNonNull;
import static org.molgenis.data.system.model.RootSystemPackage.PACKAGE_SYSTEM;

import org.molgenis.data.meta.SystemPackage;
import org.molgenis.data.meta.model.PackageMetadata;
import org.molgenis.data.system.model.RootSystemPackage;
import org.springframework.stereotype.Component;

@Component
public class GavinPackage extends SystemPackage {

  private static final String SIMPLE_NAME = "gav";
  public static final String PACKAGE_GAVIN = PACKAGE_SYSTEM + PACKAGE_SEPARATOR + SIMPLE_NAME;

  private final RootSystemPackage rootSystemPackage;

  public GavinPackage(PackageMetadata packageMetadata, RootSystemPackage rootSystemPackage) {
    super(PACKAGE_GAVIN, packageMetadata);
    this.rootSystemPackage = requireNonNull(rootSystemPackage);
  }

  @Override
  protected void init() {
    setParent(rootSystemPackage);

    setLabel("Gavin");
  }
}
