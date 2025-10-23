/**
 * Copyright 2011 Marco de Booij
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the Licence. You may
 * obtain a copy of the Licence at:
 *
 * http://www.osor.eu/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package eu.debooy.doos.domain;

import eu.debooy.doosutils.DoosUtils;
import java.io.Serializable;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;


/**
 * @author Marco de Booij
 */
public class I18nCodeTekstPK implements Serializable {
  private static final  long  serialVersionUID  = 1L;

	private Long   codeId;
	private String taalKode;

  public I18nCodeTekstPK() {}

  public I18nCodeTekstPK(Long codeId, String taalKode) {
    this.codeId   = codeId;
    this.taalKode = taalKode;
  }

  @Override
   public boolean equals(Object object) {
     if (!(object instanceof I18nCodeTekstPK)) {
       return false;
     }
     var i18nCodeWaardePK  = (I18nCodeTekstPK) object;
     return new EqualsBuilder().append(codeId, i18nCodeWaardePK.codeId)
                               .append(taalKode, i18nCodeWaardePK.taalKode)
                               .isEquals();
   }

  public Long getCodeId() {
    return codeId;
  }

  public String getTaalKode() {
    return taalKode;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(codeId).append(taalKode).toHashCode();
  }

  public void setCodeId(Long codeId) {
    this.codeId   = codeId;
  }

  public void setTaalKode(String taalKode) {
    this.taalKode = DoosUtils.stripToLowerCase(taalKode);
  }

  @Override
  public String toString() {
    return new StringBuilder().append("I18nCodeTekstPK")
                              .append(" (codeId=").append(codeId)
                              .append(", taalKode=").append(taalKode)
                              .append(")").toString();
  }
}