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
import eu.debooy.doosutils.errorhandling.exception.ObjectNotFoundException;
import eu.debooy.doosutils.errorhandling.exception.base.DoosLayer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapKey;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;


/**
 * @author Marco de Booij
 */
@Entity
@Table(name="I18N_CODES", schema="DOOS")
@NamedQuery(name="i18ncodeCode",
            query="select i from I18nCodeDto i where i.code=:code")
public class I18nCodeDto extends Dto implements Comparable<I18nCodeDto> {
  private static final  long  serialVersionUID  = 1L;

  public static final String  COL_CODE    = "code";
  public static final String  COL_CODEID  = "codeId";

  public static final String  PAR_CODE    = "code";

  public static final String  QRY_CODE    = "i18ncodeCode";

  @Column(name="CODE", length=75, nullable=false, unique=true)
  private String  code;
  @Id
  @GeneratedValue(strategy=GenerationType.IDENTITY)
  @Column(name="CODE_ID", nullable=false)
  private Long    codeId;

  @OneToMany(cascade=CascadeType.ALL, fetch=FetchType.EAGER, targetEntity=I18nCodeTekstDto.class, orphanRemoval=true)
  @JoinColumn(name="CODE_ID", referencedColumnName="CODE_ID", nullable=false, updatable=false, insertable=true)
  @MapKey(name="taalKode")
  private Map<String, I18nCodeTekstDto> teksten = new HashMap<>();

  public I18nCodeDto() {}

  public I18nCodeDto(String code) {
    this.code = code;
  }

  public void addTekst(I18nCodeTekstDto i18nCodeTekstDto) {
    if (null == i18nCodeTekstDto.getCodeId()) {
      i18nCodeTekstDto.setCodeId(codeId);
    }
    teksten.put(i18nCodeTekstDto.getTaalKode(), i18nCodeTekstDto);
  }

  @Override
  public int compareTo(I18nCodeDto i18nCode) {
    return new CompareToBuilder().append(code, i18nCode.code)
                                 .toComparison();
  }

  public boolean containsTekst(String taalKode) {
    return teksten.containsKey(taalKode);
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof I18nCodeDto)) {
      return false;
    }

    var i18nCode  = (I18nCodeDto) object;
    return new EqualsBuilder().append(code, i18nCode.code).isEquals();
  }

  public String getCode() {
    return code;
  }

  public Long getCodeId() {
    return codeId;
  }

  public I18nCodeTekstDto getTekst(String taalKode) {
    if (teksten.containsKey(taalKode)) {
      return teksten.get(taalKode);
    } else {
      throw new ObjectNotFoundException(DoosLayer.PERSISTENCE, taalKode);
    }
  }

  public Collection<I18nCodeTekstDto> getTeksten() {
    return teksten.values();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(code).toHashCode();
  }

  @Transient
  public boolean hasTekst(String taalKode) {
    return teksten.containsKey(taalKode);
  }

  public void removeTekst(I18nCodeTekstDto i18nCodeTekstDto) {
    removeTekst(i18nCodeTekstDto.getTaalKode());
  }

  public void removeTekst(String taalKode) {
    if (teksten.containsKey(taalKode)) {
      teksten.remove(taalKode);
    } else {
      throw new ObjectNotFoundException(DoosLayer.PERSISTENCE, taalKode);
    }
  }

  public void setCode(String code) {
    this.code   = DoosUtils.strip(code);
  }

  public void setCodeId(Long codeId) {
    this.codeId = codeId;
  }

  public void setTeksten(Collection<I18nCodeTekstDto> teksten) {
    for (I18nCodeTekstDto tekst : teksten) {
      this.teksten.put(tekst.getTaalKode(), tekst);
    }
  }
}