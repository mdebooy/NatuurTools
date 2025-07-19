/*
 * Copyright (c) 2024 Marco de Booij
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

package eu.debooy.natuurtools;

import eu.debooy.doosutils.Batchjob;
import eu.debooy.doosutils.ComponentsConstants;
import eu.debooy.doosutils.DoosBanner;
import eu.debooy.doosutils.DoosUtils;
import eu.debooy.doosutils.ParameterBundle;
import eu.debooy.doosutils.access.BestandConstants;
import eu.debooy.doosutils.access.CsvBestand;
import eu.debooy.doosutils.errorhandling.exception.DuplicateObjectException;
import eu.debooy.doosutils.errorhandling.exception.base.DoosRuntimeException;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.doosutils.percistence.DbConnection;
import eu.debooy.natuur.NatuurConstants;
import eu.debooy.natuur.domain.TaxonDto;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;


/**
 * @author Marco de Booij
 */
public class Hernoem extends Batchjob {
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  private static final  Map<String, String> taxa  = new TreeMap<>();

  private static  EntityManager em;
  private static  Integer       gewijzigd = 0;
  private static  Integer       nieuwe    = 0;

  protected Hernoem() {}

  public static void execute(String[] args) {
    setParameterBundle(
        new ParameterBundle.Builder()
                           .setArgs(args)
                           .setBanner(new DoosBanner())
                           .setBaseName(NatuurTools.TOOL_HERNOEM)
                           .build());

    if (!paramBundle.isValid()) {
      return;
    }

    laadCsv();

    try (var dbConn =
        new DbConnection.Builder()
              .setDbUser(paramBundle.getString(NatuurTools.PAR_DBUSER))
              .setDbUrl(paramBundle.getString(NatuurTools.PAR_DBURL))
              .setWachtwoord(paramBundle.getString(NatuurTools.PAR_WACHTWOORD))
              .setPersistenceUnitName(NatuurTools.EM_UNITNAME)
              .build()) {
      em  = dbConn.getEntityManager();

      taxa.forEach(Hernoem::hernoemTaxon);
    } catch (Exception e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }

    DoosUtils.naarScherm();
    DoosUtils.naarScherm(
        MessageFormat.format(resourceBundle.getString(NatuurTools.MSG_GELEZEN),
                             String.format("%,6d", taxa.size())));
    DoosUtils.naarScherm(
        MessageFormat.format(resourceBundle.getString(NatuurTools.MSG_UPDATED),
                             String.format("%,6d", gewijzigd)));
    DoosUtils.naarScherm(
        MessageFormat.format(resourceBundle
                              .getString(NatuurTools.MSG_AANTALNIEUW),
                             String.format("%,6d", nieuwe)));
    klaar();
  }

  private static TaxonDto getParent(String huidig, String nieuw) {
    var huidigeDelen  = huidig.split(" ");
    var huidigeParent =
        getTaxon(String.join(" ", Arrays.copyOfRange(huidigeDelen, 0,
                                                     huidigeDelen.length-1)));
    var nieuweDelen   = nieuw.split(" ");
    var latijnsenaam  = String.join(" ", Arrays.copyOfRange(nieuweDelen, 0,
                                                     nieuweDelen.length-1));
    var nieuweParent  = getTaxon(latijnsenaam);

    if (null == nieuweParent.getTaxonId()) {
      DoosUtils.naarScherm(
          MessageFormat.format(resourceBundle.getString(NatuurTools.MSG_NIEUW),
                               "", huidigeParent.getRang(), latijnsenaam));

      nieuweParent.setLatijnsenaam(latijnsenaam);
      nieuweParent.setParentId(huidigeParent.getParentId());
      nieuweParent.setRang(huidigeParent.getRang());
      nieuweParent.setVolgnummer(0L);

      nieuweParent  = setTaxon(nieuweParent);
      nieuwe++;
    }

    return nieuweParent;
  }

  private static TaxonDto getTaxon(String latijnsenaam) {
    var query = em.createNamedQuery(TaxonDto.QRY_LATIJNSENAAM);
    query.setParameter(TaxonDto.PAR_LATIJNSENAAM, latijnsenaam);

    try {
      return (TaxonDto) query.getSingleResult();
    } catch (NoResultException e) {
      return new TaxonDto();
    }
  }

  private static void taxaKinderen(String huidig, String nieuw,
                                      Long parentId) {
    var query = em.createNamedQuery(TaxonDto.QRY_KINDEREN);
    query.setParameter(TaxonDto.PAR_OUDER, parentId);
    var kinderen  = query.getResultList();

    for (var kind : kinderen) {
      var taxon         = (TaxonDto) kind;
      var latijnsenaam  = taxon.getLatijnsenaam();
      if (latijnsenaam.startsWith(huidig+" ")) {
        DoosUtils.foutNaarScherm(
            MessageFormat.format(
                resourceBundle.getString(NatuurTools.MSG_HERNOEM),
                huidig, nieuw));
        var nieuweLatijsenaam = latijnsenaam.replaceFirst(huidig, nieuw);
        taxon.setLatijnsenaam(nieuweLatijsenaam);
        try {
          setTaxon(taxon);
          gewijzigd++;
        } catch (DuplicateObjectException e) {
          DoosUtils.foutNaarScherm(
              MessageFormat.format(
                  resourceBundle.getString(NatuurTools.MSG_BESTAANTAL),
                  latijnsenaam));
        } catch (DoosRuntimeException e) {
          DoosUtils.foutNaarScherm(
              String.format(ComponentsConstants.ERR_RUNTIME,
                            e.getLocalizedMessage()));
        }

        taxaKinderen(huidig, nieuw, taxon.getTaxonId());
      }
    }
  }

  private static void hernoemTaxon(String huidig, String nieuw) {
    DoosUtils.foutNaarScherm(
        MessageFormat.format(
            resourceBundle.getString(NatuurTools.MSG_HERNOEM),
            huidig, nieuw));

    var taxon = getTaxon(huidig);

    if (null == taxon.getTaxonId()) {
      DoosUtils.foutNaarScherm(
          MessageFormat.format(
              resourceBundle.getString(NatuurTools.MSG_BESTAANTNIET),
              huidig));

      return;
    }

    var check = getTaxon(nieuw);

    if (null != check.getTaxonId()) {
      DoosUtils.foutNaarScherm(
          MessageFormat.format(
              resourceBundle.getString(NatuurTools.MSG_BESTAANTAL),
              nieuw));

      return;
    }

    taxon.setLatijnsenaam(nieuw);
    taxaKinderen(huidig, nieuw, taxon.getTaxonId());
    if (taxon.getRang().equals(NatuurConstants.RANG_SOORT)
        || taxon.getRang().equals(NatuurConstants.RANG_ONDERSOORT)) {
      var parent  = getParent(huidig, nieuw);
      taxon.setParentId(parent.getTaxonId());
    }

    setTaxon(taxon);

    gewijzigd++;
  }

  private static void laadCsv() {
    try (var csvBestand =
          new CsvBestand.Builder()
                        .setBestand(
                            paramBundle.getBestand(PAR_CSVBESTAND,
                                                   BestandConstants.EXT_CSV))
                        .setCharset(paramBundle.getString(PAR_CHARSETIN))
                        .setHeader(false)
                        .build()) {
      while (csvBestand.hasNext()) {
        var veld    = csvBestand.next();
        taxa.put(DoosUtils.strip(veld[0]), DoosUtils.strip(veld[1]));
      }
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }
  }

  private static TaxonDto setTaxon(TaxonDto taxon) {
    em.getTransaction().begin();
    TaxonDto  updated;
    if (null == taxon.getTaxonId()) {
      updated = em.merge(taxon);
    } else {
    updated = taxon;
    }

    em.persist(updated);
    em.getTransaction().commit();

    return updated;
  }
}
