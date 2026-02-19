/**
 * Copyright (c) 2020 Marco de Booij
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

import eu.debooy.doos.domain.TaalDto;
import eu.debooy.doos.domain.TaalnaamDto;
import eu.debooy.doosutils.Batchjob;
import eu.debooy.doosutils.DoosBanner;
import eu.debooy.doosutils.DoosUtils;
import eu.debooy.doosutils.ParameterBundle;
import eu.debooy.doosutils.access.CsvBestand;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.doosutils.percistence.DbConnection;
import eu.debooy.natuur.NatuurConstants;
import eu.debooy.natuur.NatuurUtils;
import eu.debooy.natuur.domain.TaxonDto;
import jakarta.persistence.EntityManager;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


/**
 * @author Marco de Booij
 */
public class IocData extends Batchjob {
  private static final  JSONObject      familie         = new JSONObject();
  private static final  JSONArray       families        = new JSONArray();
  private static final  JSONObject      geslacht        = new JSONObject();
  private static final  JSONArray       geslachten      = new JSONArray();
  private static final  JSONObject      ondersoort      = new JSONObject();
  private static final  JSONArray       ondersoorten    = new JSONArray();
  private static final  JSONObject      orde            = new JSONObject();
  private static final  JSONArray       ordes           = new JSONArray();
  private static final  JSONParser      parser          = new JSONParser();
  private static final  List<String>    rangen          = new ArrayList<>();
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());
  private static final  JSONObject      soort           = new JSONObject();
  private static final  JSONArray       soorten         = new JSONArray();
  private static final  Map<String, Integer>
                                        taaltotalen     = new HashMap<>();
  private static final  Map<String, Integer>
                                        totalen         = new HashMap<>();

  private static  Integer   factor        = NatuurConstants.VOLGNUMMERFACTOR;
  private static  boolean   perRang       = false;
  private static  Integer   sequence      = 0;
  private static  String    strtaal       = "";
  private static  String[]  taalkolom;
  private static  String    vorigGeslacht = "";
  private static  String    vorigeSoort   = "";

  private static final  Map<String, String> cache     = new TreeMap<>();
  private static final  Set<String>         taal      = new TreeSet<>();
  private static final  Map<String, String> taalnaam  = new TreeMap<>();

  protected IocData() {}

  private static void addRang(String rang) {
    totalen.put(rang, totalen.get(rang)+1);
    sequence++;
  }

  private static void addTaal(String taal) {
    taaltotalen.computeIfAbsent(taal, k -> 0);
    taaltotalen.put(taal, taaltotalen.get(taal)+1);
  }

  public static void execute(String[] args) {
    setParameterBundle(
        new ParameterBundle.Builder()
                           .setArgs(args)
                           .setBanner(new DoosBanner())
                           .setBaseName(NatuurTools.TOOL_IOCDATA)
                           .build());

    if (!paramBundle.isValid()) {
      return;
    }

    perRang     = paramBundle.getBoolean(NatuurTools.PAR_PERRANG);
    if (paramBundle.containsArgument(NatuurTools.PAR_TALEN)) {
      taal.addAll(Set.of(paramBundle.getString(NatuurTools.PAR_TALEN)
                                    .split(",")));
    }
    setRangen();

    if (paramBundle.containsArgument(NatuurTools.PAR_FACTOR)) {
      factor  = paramBundle.getInteger(NatuurTools.PAR_FACTOR);
    }

    var taxa    = new JSONObject();
    verwerkNamen();
    var lijnen  = verwerkStructuur(taxa);

    NatuurTools.writeJson(paramBundle.getBestand(NatuurTools.PAR_JSON),
                          taxa, paramBundle.getString(PAR_CHARSETUIT));

    var melding =
        MessageFormat.format(resourceBundle.getString(NatuurTools.MSG_TALEN),
                             taalnaam).replace("[, ", "[");
    DoosUtils.naarScherm(melding.indexOf("[") + 1, melding, 80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(
        MessageFormat.format(resourceBundle.getString(NatuurTools.MSG_LIJNEN),
                             String.format("%,9d", lijnen)));
    rangen.forEach(rang -> {
      if (totalen.get(rang) > 0) {
        DoosUtils.naarScherm(String.format("%6s : %,9d",
                                           rang, totalen.get(rang)));
      }
    });
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(String.format("%25s   %9s",
                      resourceBundle.getString(NatuurTools.LBL_TALEN),
                      resourceBundle.getString(NatuurTools.LBL_AANTAL)));
    DoosUtils.naarScherm(DoosUtils.stringMetLengte("-", 37, "-"));
    taalnaam.forEach((k, v) -> {
      if (taaltotalen.containsKey(k)) {
        var totaal  = taaltotalen.get(k);
        if (totaal > 0) {
          DoosUtils.naarScherm(String.format("%25s : %,9d", v, totaal));
        }
      }
    });

    klaar();
  }

  private static int getVolgnummer(String rang) {
    if (!perRang) {
      return sequence;
    }

    return totalen.get(rang);
  }

  private static void nieuwGeslacht() throws ParseException {
    nieuweSoort();
    if (!geslacht.isEmpty()) {
      if (!soorten.isEmpty()) {
        geslacht.put(NatuurTools.KEY_SUBRANGEN,
                     parser.parse(soorten.toString()));
        soorten.clear();
      }
      geslachten.add(parser.parse(geslacht.toString()));
      geslacht.clear();
    }
  }

  private static void nieuweFamilie() throws ParseException {
    nieuwGeslacht();
    if (!familie.isEmpty()) {
      if (!geslachten.isEmpty()) {
        familie.put(NatuurTools.KEY_SUBRANGEN,
                    parser.parse(geslachten.toString()));
        geslachten.clear();
      }
      families.add(parser.parse(familie.toString()));
      familie.clear();
    }
  }

  private static void nieuweOrde() throws ParseException {
    nieuweFamilie();
    if (!orde.isEmpty()) {
      if (!families.isEmpty()) {
        orde.put(NatuurTools.KEY_SUBRANGEN, parser.parse(families.toString()));
        families.clear();
      }
      ordes.add(parser.parse(orde.toString()));
      orde.clear();
    }
  }

  private static void nieuweSoort() throws ParseException {
    if (!soort.isEmpty()) {
      if (!ondersoorten.isEmpty()) {
        soort.put(NatuurTools.KEY_SUBRANGEN,
                  parser.parse(ondersoorten.toString()));
        ondersoorten.clear();
      }
      var latijn  = (String) soort.get(NatuurTools.KEY_LATIJN);
      if (cache.containsKey(latijn)) {
        soort.put(NatuurTools.KEY_NAMEN, parser.parse(cache.get(latijn)));
      }
      soorten.add(parser.parse(soort.toString()));
      soort.clear();
    }
  }

  private static void setRangen() {
    for (String rang : new String[] {NatuurConstants.RANG_ORDE,
                                     NatuurConstants.RANG_FAMILIE,
                                     NatuurConstants.RANG_GESLACHT,
                                     NatuurConstants.RANG_SOORT,
                                     NatuurConstants.RANG_ONDERSOORT}) {
      rangen.add(rang);
      totalen.put(rang, 0);
    }
  }

  private static String taalUitDatabase(String csvtaal, String gebruikerstaal,
                                        List<TaalnaamDto> taalnaamDto, int i,
                                        EntityManager em) {
    var taalDto = em.find(TaalDto.class, taalnaamDto.get(0).getTaalId());
    if (taalDto.getIso6392t().equals(csvtaal)) {
      strtaal = taalDto.getIso6392t();
    }
    if (taal.isEmpty()
        || taal.contains(taalDto.getIso6392t())) {
      taalkolom[i]  = taalDto.getIso6392t();
      if (taalDto.hasTaalnaam(gebruikerstaal)) {
        taalnaam.put(taalDto.getIso6392t(),
                     String.format("%s (%s)",
                                   taalDto.getNaam(gebruikerstaal),
                                   taalDto.getIso6392t()));
      } else {
        taalnaam.put(taalkolom[i], taalkolom[i]);
      }
    } else {
      taalkolom[i]  = "";
    }

    return csvtaal;
  }

  private static void talenUitParameter() {
    strtaal   = paramBundle.getString(PAR_TAAL);
    taalkolom = paramBundle.getString(NatuurTools.PAR_TALEN).split(",");
    for (var element : taalkolom) {
      taalnaam.put(element, element);
    }
  }

  private static void verwerkHeader() {
    if (!paramBundle.containsArgument(NatuurTools.PAR_DBURL)) {
      talenUitParameter();
      sequence  = factor
                    * paramBundle.getInteger(NatuurTools.PAR_KLASSEVOLGNUMMER);
      return;
    }

    try (var dbConn =
        new DbConnection.Builder()
              .setDbUser(paramBundle.getString(NatuurTools.PAR_DBUSER))
              .setDbUrl(paramBundle.getString(NatuurTools.PAR_DBURL))
              .setWachtwoord(paramBundle.getString(NatuurTools.PAR_WACHTWOORD))
              .setPersistenceUnitName(NatuurTools.EM_UNITNAME)
              .build()) {
      var csvtaal         = paramBundle.getString(PAR_TAAL);
      var em              = dbConn.getEntityManager();

      var query           = em.createNamedQuery(TaxonDto.QRY_LATIJNSENAAM);
      query.setParameter(TaxonDto.PAR_LATIJNSENAAM, NatuurConstants.LAT_VOGELS);
      var klasse          = (TaxonDto) query.getSingleResult();
      sequence            = factor * klasse.getVolgnummer().intValue();

      var gebruikerstaal  =
          ((TaalDto)  em.createNamedQuery(TaalDto.QRY_TAAL_ISO6391)
                        .setParameter(TaalDto.PAR_ISO6391,
                                      Locale.getDefault().getLanguage())
                        .getSingleResult()).getIso6392t();
      var naamquery       = em.createNamedQuery(TaalnaamDto.QRY_METNAAM);

      for (var i =0; i < taalkolom.length; i++) {
        naamquery.setParameter(TaalnaamDto.PAR_TAAL, csvtaal);
        naamquery.setParameter(TaalnaamDto.PAR_NAAM, taalkolom[i]);
        var taalnaamDto = naamquery.getResultList();

        if (taalnaamDto.isEmpty()) {
          taalkolom[i]  = "";
        } else {
          taalUitDatabase(csvtaal, gebruikerstaal, taalnaamDto, i, em);
        }
      }
    } catch (Exception e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
      taalkolom = new String[0];
    }
  }

  private static void verwerkNamen() {
    try (var csvBestand  =
          new CsvBestand.Builder()
                        .setBestand(
                            paramBundle.getBestand(NatuurTools.PAR_IOCNAMEN))
                        .setCharset(paramBundle.getString(PAR_CHARSETIN))
                        .setHeader(true)
                        .build()) {

      taalkolom = Arrays.copyOfRange(csvBestand.getKolomNamen(), 4,
                                     csvBestand.getKolomNamen().length);
      verwerkHeader();

      if (taalkolom.length == 0) {
        return;
      }

      var namen = new JSONObject();
      while(csvBestand.hasNext()) {
        var veld          = csvBestand.next();
        var latijnsenaam  = NatuurUtils.formatLatijnsenaam(veld[3]);

        namen.clear();
        for (var i = 0; i < taalkolom.length; i++) {
          if (DoosUtils.isNotBlankOrNull(taalkolom[i])
              && DoosUtils.isNotBlankOrNull(veld[i+4])) {
            namen.put(taalkolom[i], veld[i+4]);
            addTaal(taalkolom[i]);
          }
        }
        cache.put(latijnsenaam, namen.toString());
      }
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(String.format("%s: %s",
              paramBundle.getBestand(NatuurTools.PAR_IOCNAMEN),
                                             e.getLocalizedMessage()));
    }
  }

  private static int verwerkStructuur(JSONObject taxa) {
    var lijnen  = 0;
    try (var csvBestand  =
          new CsvBestand.Builder()
                        .setBestand(
                            paramBundle
                                .getBestand(NatuurTools.PAR_IOCSTRUCTUUR))
                        .setCharset(paramBundle.getString(PAR_CHARSETIN))
                        .setHeader(false)
                        .build()) {

      while (csvBestand.hasNext()) {
        lijnen++;
        verwerkStructuurLijn(csvBestand.next());
      }

      nieuweOrde();
      taxa.put(NatuurTools.KEY_RANG, NatuurConstants.RANG_KLASSE);
      taxa.put(NatuurTools.KEY_LATIJN, NatuurConstants.LAT_VOGELS);
      taxa.put(NatuurTools.KEY_SUBRANGEN, ordes);
    } catch (BestandException | ParseException e) {
      DoosUtils.foutNaarScherm(String.format("%s: %s",
              paramBundle.getBestand(NatuurTools.PAR_IOCSTRUCTUUR),
                                             e.getLocalizedMessage()));
    }

    return lijnen;
  }

  private static void verwerkStructuurLijn(String[] veld)
      throws ParseException {
    // Nieuwe Orde
    if (DoosUtils.isNotBlankOrNull(veld[0])) {
      nieuweOrde();
      addRang(NatuurConstants.RANG_ORDE);
      orde.put(NatuurTools.KEY_SEQ, getVolgnummer(NatuurConstants.RANG_ORDE));
      orde.put(NatuurTools.KEY_RANG, NatuurConstants.RANG_ORDE);
      orde.put(NatuurTools.KEY_LATIJN, NatuurUtils.formatLatijnsenaam(veld[0]));
      if (NatuurUtils.isUitgestorven(veld[0])) {
        orde.put(NatuurTools.KEY_STATUS, NatuurConstants.STAT_UITGESTORVEN);
      }
    }
    // Nieuwe familie
    if (DoosUtils.isNotBlankOrNull(veld[1])) {
      nieuweFamilie();
      addRang(NatuurConstants.RANG_FAMILIE);
      familie.put(NatuurTools.KEY_SEQ,
                  getVolgnummer(NatuurConstants.RANG_FAMILIE));
      familie.put(NatuurTools.KEY_RANG, NatuurConstants.RANG_FAMILIE);
      familie.put(NatuurTools.KEY_LATIJN,
                  NatuurUtils.formatLatijnsenaam(veld[1]));
      if (NatuurUtils.isUitgestorven(veld[1])) {
        familie.put(NatuurTools.KEY_STATUS, NatuurConstants.STAT_UITGESTORVEN);
      }
      if (DoosUtils.isNotBlankOrNull(veld[2])) {
        var namen = new JSONObject();
        namen.put(strtaal, veld[2]);
        familie.put(NatuurTools.KEY_NAMEN, namen);
      }
    }

    // Nieuw geslacht
    if (DoosUtils.isNotBlankOrNull(veld[3])) {
      nieuwGeslacht();
      addRang(NatuurConstants.RANG_GESLACHT);
      vorigGeslacht = NatuurUtils.formatLatijnsenaam(veld[3]);
      geslacht.put(NatuurTools.KEY_SEQ,
                   getVolgnummer(NatuurConstants.RANG_GESLACHT));
      geslacht.put(NatuurTools.KEY_RANG, NatuurConstants.RANG_GESLACHT);
      geslacht.put(NatuurTools.KEY_LATIJN, vorigGeslacht);
      if (NatuurUtils.isUitgestorven(veld[3])) {
        geslacht.put(NatuurTools.KEY_STATUS, NatuurConstants.STAT_UITGESTORVEN);
      }
    }

    // Nieuw soort
    if (DoosUtils.isNotBlankOrNull(veld[4])) {
      nieuweSoort();
      addRang(NatuurConstants.RANG_SOORT);
      vorigeSoort = NatuurUtils.formatLatijnsenaam(vorigGeslacht + " "
                                                    + veld[4]);
      soort.put(NatuurTools.KEY_SEQ, getVolgnummer(NatuurConstants.RANG_SOORT));
      soort.put(NatuurTools.KEY_RANG, NatuurConstants.RANG_SOORT);
      soort.put(NatuurTools.KEY_LATIJN, vorigeSoort);
      if (NatuurUtils.isUitgestorven(veld[4])) {
        soort.put(NatuurTools.KEY_STATUS, NatuurConstants.STAT_UITGESTORVEN);
      }
    }

    if (DoosUtils.isNotBlankOrNull(veld[5])) {
      ondersoort.clear();
      addRang(NatuurConstants.RANG_ONDERSOORT);
      ondersoort.put(NatuurTools.KEY_SEQ,
                     getVolgnummer(NatuurConstants.RANG_ONDERSOORT));
      ondersoort.put(NatuurTools.KEY_RANG, NatuurConstants.RANG_ONDERSOORT);
      ondersoort.put(NatuurTools.KEY_LATIJN,
                     NatuurUtils.formatLatijnsenaam(vorigeSoort + " "
                                                      + veld[5]));
      if (NatuurUtils.isUitgestorven(veld[5])) {
        ondersoort.put(NatuurTools.KEY_STATUS,
                       NatuurConstants.STAT_UITGESTORVEN);
      }
      ondersoorten.add(parser.parse(ondersoort.toString()));
    }
  }
}
