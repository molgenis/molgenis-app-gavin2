package org.molgenis.app.gavin.input.model;

import static org.molgenis.app.gavin.input.model.LineType.VCF;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;

@AutoValue
public abstract class VcfVariant implements Variant {
  public abstract String getChrom();

  public abstract long getPos();

  public abstract String getId();

  public abstract String getRef();

  public abstract String getAlt();

  @Override
  public LineType getLineType() {
    return VCF;
  }

  @Override
  public String toString() {
    return Joiner.on('\t').join(getChrom(), getPos(), getId(), getRef(), getAlt(), '.', '.', '.');
  }

  public static VcfVariant create(String chrom, long pos, String id, String ref, String alt) {
    return new AutoValue_VcfVariant(chrom, pos, id, ref, alt);
  }
}
