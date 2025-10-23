/*
 * Copyright (c) 2022 Marco de Booij
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the Licence. You may
 * obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package eu.debooy.doos.domain;

import eu.debooy.doosutils.domain.Dto;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.NamedQuery;
import javax.persistence.Table;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;


/**
 * @author Marco de Booij
 */
@Entity
@Table(name="TAALNAMEN", schema="DOOS")
@IdClass(TaalnaamPK.class)
@NamedQuery(name="taalnamenInTaal",
            query="select t from TaalnaamDto t where t.iso6392t=:iso6392t")
@NamedQuery(name="taalnamenMetNaam",
            query="select t from TaalnaamDto t where t.iso6392t=:iso6392t and t.naam=:naam")
public class TaalnaamDto extends Dto implements Comparable<TaalnaamDto> {
  private static final  long  serialVersionUID  = 1L;

  public static final String  COL_ISO6392T  = "iso6392t";
  public static final String  COL_NAAM      = "naam";
  public static final String  COL_TAALID    = "taalId";

  public static final String  PAR_NAAM  = "naam";
  public static final String  PAR_TAAL  = "iso6392t";

  public static final String  QRY_INTAAL  = "taalnamenInTaal";
  public static final String  QRY_METNAAM = "taalnamenMetNaam";

  @Id
  @Column(name="ISO_639_2T", length=3, nullable=false)
  private String  iso6392t;
  @Column(name="NAAM", length=100, nullable=false)
  private String  naam;
  @Id
  @Column(name="TAAL_ID", nullable=false)
  private Long    taalId;

  @Override
  public int compareTo(TaalnaamDto taalnaamDto) {
    return new CompareToBuilder().append(taalId, taalnaamDto.taalId)
                                 .append(iso6392t, taalnaamDto.iso6392t)
                                 .toComparison();
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof TaalnaamDto)) {
      return false;
    }
    var taalnaamDto = (TaalnaamDto) object;
    return new EqualsBuilder().append(taalId, taalnaamDto.taalId)
                              .append(iso6392t, taalnaamDto.iso6392t)
                              .isEquals();
  }

  public String getIso6392t() {
    return iso6392t;
  }

  public String getNaam() {
    return naam;
  }

  public Long getTaalId() {
    return taalId;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(taalId).append(iso6392t).toHashCode();
  }
}
