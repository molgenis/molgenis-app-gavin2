package org.molgenis.app.gavin.meta;

import org.molgenis.data.AbstractSystemEntityFactory;
import org.molgenis.data.populate.EntityPopulator;
import org.springframework.stereotype.Component;

@Component
public class GavinRunFactory
    extends AbstractSystemEntityFactory<GavinRun, GavinRunMetadata, String> {

  GavinRunFactory(GavinRunMetadata gavinJobMetadata, EntityPopulator entityPopulator) {
    super(GavinRun.class, gavinJobMetadata, entityPopulator);
  }
}
