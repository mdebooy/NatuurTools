/**
 * Copyright 2011 Marco de Booij
 *
 * Licensed under de EUPL, Version 1.1 or - as soon they will be approved by
 * de European Commission - subsequent versions of de EUPL (the "Licence");
 * you may not use this work except in compliance with de Licence. You may
 * obtain a copy of de Licence at:
 *
 * http://www.osor.eu/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under de Licence is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See de Licence for de specific language governing permissions and
 * limitations under de Licence.
 */
package eu.debooy.doos.domain;

import eu.debooy.doosutils.DoosUtils;
import eu.debooy.doosutils.domain.Dto;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.NamedQuery;
import javax.persistence.OrderColumn;
import javax.persistence.Table;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;


/**
 * @author Marco de Booij
 */
@Entity
@Table(name="I18N_CODE_TEKSTEN", schema="DOOS")
@IdClass(I18nCodeTekstPK.class)
@NamedQuery(name="tekstenPerTaal",
            query="select i.taalKode, count(i.codeId) from I18nCodeTekstDto i group by i.taalKode")
public class I18nCodeTekstDto extends Dto
    implements Comparable<I18nCodeTekstDto> {
  private static final  long  serialVersionUID  = 1L;

  public static final String  COL_CODEID    = "codeId";
  public static final String  COL_TAALKODE  = "taalKode";
  public static final String  COL_TEKST     = "tekst";

  public static final String  QRY_TEKSTENPERTAAL  = "tekstenPerTaal";

  @Id
  @Column(name="CODE_ID", nullable=false)
  private Long    codeId;
  @Id
  @OrderColumn
  @Column(name="TAAL_KODE", length=2, nullable=false)
  private String  taalKode;
	@Column(name="TEKST", length=1024, nullable=false)
	private String  tekst;

  @Override
  public int compareTo(I18nCodeTekstDto i18nCodeTekstDto) {
    return new CompareToBuilder().append(codeId, i18nCodeTekstDto.codeId)
                                 .append(taalKode, i18nCodeTekstDto.taalKode)
                                 .toComparison();
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof I18nCodeTekstDto)) {
      return false;
    }
    var i18nCodeWaarde  = (I18nCodeTekstDto) object;
    return new EqualsBuilder().append(codeId, i18nCodeWaarde.codeId)
                              .append(taalKode, i18nCodeWaarde.taalKode)
                              .isEquals();
  }

  public Long getCodeId() {
    return codeId;
  }

  public I18nCodeTekstPK getId() {
    return new I18nCodeTekstPK(codeId, taalKode);
  }

  public String getTaalKode() {
    return taalKode;
  }

  public String getTekst() {
    return tekst;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(codeId).append(taalKode).toHashCode();
  }

  public void setCodeId(Long codeId) {
    this.codeId = codeId;
  }

  public void setId(I18nCodeTekstPK id) {
    codeId    = id.getCodeId();
    taalKode  = id.getTaalKode();
  }

  public void setTaalKode(String taalKode) {
    this.taalKode = DoosUtils.strip(taalKode);
  }

  public void setTekst(String tekst) {
    this.tekst    = DoosUtils.strip(tekst);
  }
}