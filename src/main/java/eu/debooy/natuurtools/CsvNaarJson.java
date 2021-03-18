/*
 * Copyright (c) 2021 Marco de Booij
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

import eu.debooy.doosutils.Arguments;
import eu.debooy.doosutils.Banner;
import eu.debooy.doosutils.Batchjob;
import static eu.debooy.doosutils.Batchjob.setBestandParameter;
import static eu.debooy.doosutils.Batchjob.setDirParameter;
import eu.debooy.doosutils.DoosUtils;
import eu.debooy.doosutils.access.CsvBestand;
import eu.debooy.doosutils.components.Message;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.natuur.domain.RangDto;
import eu.debooy.natuur.domain.TaxonDto;
import static eu.debooy.natuur.domain.TaxonDto.PAR_LATIJNSENAAM;
import static eu.debooy.natuur.domain.TaxonDto.QRY_LATIJNSENAAM;
import java.io.File;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


/**
 * @author Marco de Booij
 */
public class CsvNaarJson extends Batchjob {
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  private static final  String  RANG_GESLACHT   = "ge";
  private static final  String  RANG_ONDERSOORT = "oso";
  private static final  String  RANG_SOORT      = "so";

  private static final  Map<String, JSONObject> jsonRang    = new HashMap<>();
  private static final  Map<String, JSONArray>  jsonRangen  = new HashMap<>();
  private static final  JSONParser              parser      = new JSONParser();
  private static final  List<String>            rangen      = new ArrayList<>();
  private static final  Map<String, Integer>    totalen     = new HashMap<>();

  private static  EntityManager em;
  private static  Integer       lijnen      = 0;
  private static  String        root        = "";
  private static  String        taal        = "";
  private static  String        vorigeRang  = "";

  private CsvNaarJson() {}

  private static void controleerHierarchie(String rang, String latijnsenaam)
      throws ParseException {
    // Genereer het geslacht als deze niet in het bestand staat.
    if (rang.equals(RANG_SOORT)) {
      genereerRang(RANG_GESLACHT, latijnsenaam.split(" ")[0]);
    }

    // Genereer het soort als deze niet in het bestand staat.
    if (rang.equals(RANG_ONDERSOORT)) {
      String[]  woorden = latijnsenaam.split(" ");
      genereerRang(RANG_SOORT, woorden[0] + " " + woorden[1]);
    }

    if (!rang.equals(vorigeRang)) {
      if (rangen.indexOf(vorigeRang) > rangen.indexOf(rang)) {
        samenvoegen(rang);
      }
      vorigeRang  = rang;
    }
  }

  public static void execute(String[] args) {
    Banner.printDoosBanner(resourceBundle.getString("banner.csvnaarjson"));

    if (!setParameters(args)) {
      return;
    }

    if (parameters.containsKey(NatuurTools.PAR_WACHTWOORD)) {
      em  = NatuurTools.getEntityManager(
                parameters.get(NatuurTools.PAR_DBUSER),
                parameters.get(NatuurTools.PAR_DBURL),
                parameters.get(NatuurTools.PAR_WACHTWOORD));
    } else {
      em  = NatuurTools.getEntityManager(
                parameters.get(NatuurTools.PAR_DBUSER),
                parameters.get(NatuurTools.PAR_DBURL));
    }

    taal  = parameters.get(PAR_TAAL);

    String[]    taxoninfo   = parameters.get(NatuurTools.PAR_TAXAROOT)
                                        .split(",");
    getRangen();

    CsvBestand  csvBestand  = null;
    JSONObject  namen       = new JSONObject();
    TaxonDto    parent      = getTaxon(taxoninfo[1]);

    root        = parent.getRang();
    vorigeRang  = root;

    jsonRang.get(root).put(NatuurTools.KEY_LATIJN, parent.getLatijnsenaam());
    jsonRang.get(root).put(NatuurTools.KEY_RANG, root);
    jsonRang.get(root).put(NatuurTools.KEY_SEQ, parent.getVolgnummer());
    parent.getTaxonnamen().forEach(naam -> namen.put(naam.getTaal(),
                                                     naam.getNaam()));
    jsonRang.get(root).put(NatuurTools.KEY_NAMEN, namen);

    try {
      csvBestand =
          new CsvBestand.Builder()
                        .setBestand(parameters.get(PAR_INVOERDIR)
                                  + parameters.get(PAR_CSVBESTAND)
                                  + EXT_CSV)
                        .setCharset(parameters.get(PAR_CHARSETIN))
                        .setHeader(false)
                        .build();

      verwerkCsv(csvBestand);
    } catch (BestandException | ParseException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    } finally {
      if (null != csvBestand) {
        try {
          csvBestand.close();
        } catch (BestandException e) {
          DoosUtils.foutNaarScherm("json: " + e.getLocalizedMessage());
        }
      }
    }

    try {
      samenvoegen(root);

      NatuurTools.writeJson(parameters.get(PAR_UITVOERDIR)
                             + parameters.get(PAR_JSONBESTAND) + EXT_JSON,
                            jsonRang.get(root),
                            parameters.get(PAR_CHARSETUIT));
    } catch (ParseException e) {
      DoosUtils.foutNaarScherm("json: " + e.getLocalizedMessage());
    }

    DoosUtils.naarScherm();
    rangen.forEach(rang -> {
      Integer volgnummer  = totalen.get(rang);
      if (volgnummer > 0) {
        DoosUtils.naarScherm(String.format("%6s: %,6d",
                rang, totalen.get(rang)));
      }
    });
    DoosUtils.naarScherm(String.format("lijnen: %,6d", lijnen));
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(getMelding(MSG_KLAAR));
    DoosUtils.naarScherm();
  }

  private static void genereerRang(String rang, String latijnsenaam)
      throws ParseException {
    if (!jsonRang.get(rang).isEmpty()) {
      if (!jsonRang.get(rang).get(NatuurTools.KEY_LATIJN)
                  .equals(latijnsenaam)) {
        verwerkTaxon(rang, latijnsenaam, "");
      }
    } else {
      verwerkTaxon(rang, latijnsenaam, "");
    }
  }

  private static void getRangen() {
    em.getTransaction().begin();
    List<RangDto> ranglijst =
        em.createQuery("select r from RangDto r order by r.niveau")
          .getResultList();
    em.getTransaction().commit();

    ranglijst.forEach(rang -> {
      jsonRang.put((rang.getRang()), new JSONObject());
      jsonRangen.put((rang.getRang()), new JSONArray());
      rangen.add(rang.getRang());
      totalen.put(rang.getRang(), 0);
    });
  }

  private static TaxonDto getTaxon(String latijnsenaam) {
    em.getTransaction().begin();
    Query query = em.createNamedQuery(QRY_LATIJNSENAAM);
    em.getTransaction().commit();
    query.setParameter(PAR_LATIJNSENAAM, latijnsenaam);
    TaxonDto  resultaat;
    try {
      resultaat = (TaxonDto) query.getSingleResult();
    } catch (NoResultException e) {
      resultaat = new TaxonDto();
    }

    return resultaat;
  }

  private static Integer getVolgnummer(String rang) {
    Integer volgnummer  = totalen.get(rang) + 1;

    totalen.put(rang, volgnummer);

    return volgnummer;
  }

  public static void help() {
    DoosUtils.naarScherm("java -jar NatuurTools.jar CsvNaarJson "
        + getMelding(LBL_OPTIE) + " "
        + MessageFormat.format(
              getMelding(LBL_PARAM), PAR_CSVBESTAND,
              resourceBundle.getString(NatuurTools.LBL_CSVBESTAND)) + " "
        + MessageFormat.format(
              getMelding(LBL_PARAM), NatuurTools.PAR_DBURL,
              resourceBundle.getString(NatuurTools.LBL_DBURL)) + " "
        + MessageFormat.format(
              getMelding(LBL_PARAM), NatuurTools.PAR_DBUSER,
              resourceBundle.getString(NatuurTools.LBL_DBUSER)) + " "
        + MessageFormat.format(
              getMelding(LBL_PARAM), NatuurTools.PAR_TAXAROOT,
              resourceBundle.getString(NatuurTools.LBL_TAXAROOT)), 80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(getParameterTekst(PAR_CHARSETIN, 12),
        MessageFormat.format(getMelding(HLP_CHARSETIN),
                             Charset.defaultCharset().name()), 80);
    DoosUtils.naarScherm(getParameterTekst(PAR_CHARSETUIT, 12),
        MessageFormat.format(getMelding(HLP_CHARSETUIT),
                             Charset.defaultCharset().name()), 80);
    DoosUtils.naarScherm(getParameterTekst(PAR_CSVBESTAND, 12),
                         resourceBundle.getString(NatuurTools.HLP_CSVBESTAND),
                         80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_DBURL, 12),
                         resourceBundle.getString(NatuurTools.HLP_DBURL), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_DBUSER, 12),
                         resourceBundle.getString(NatuurTools.HLP_DBUSER), 80);
    DoosUtils.naarScherm(getParameterTekst(PAR_INVOERDIR, 12),
                         getMelding(HLP_INVOERDIR), 80);
    DoosUtils.naarScherm(getParameterTekst(PAR_JSONBESTAND, 12),
                         resourceBundle.getString(NatuurTools.HLP_IOCJSON),
                         80);
    DoosUtils.naarScherm(getParameterTekst(PAR_TAAL, 12),
        MessageFormat.format(resourceBundle.getString(NatuurTools.HLP_TAAL),
                             Locale.getDefault().getLanguage()), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_TAXAROOT, 12),
                         resourceBundle.getString(NatuurTools.HLP_TAXAROOT),
                         80);
    DoosUtils.naarScherm(getParameterTekst(PAR_UITVOERDIR, 12),
                         getMelding(HLP_UITVOERDIR), 80);
    DoosUtils.naarScherm(getParameterTekst(NatuurTools.PAR_WACHTWOORD, 12),
                         resourceBundle.getString(NatuurTools.HLP_WACHTWOORD),
                         80);
    DoosUtils.naarScherm();
    DoosUtils.naarScherm(
        MessageFormat.format(getMelding(HLP_PARAMSVERPLICHT),
                             PAR_CSVBESTAND + ", " + NatuurTools.PAR_DBURL
                              + ", " + NatuurTools.PAR_DBUSER,
                             NatuurTools.PAR_TAXAROOT),
                             80);
    DoosUtils.naarScherm();
  }

  protected static void printMessages(List<Message> fouten) {
    fouten.forEach(fout ->
      DoosUtils.foutNaarScherm(getMelding(LBL_FOUT, fout.toString())));
  }

  private static void samenvoegen(String root) throws ParseException {
    Integer iRoot   = rangen.indexOf(root);
    String  laatste = rangen.get(rangen.size()-1);

    // Laagste rang kan geen subrangen hebben.
    if (!jsonRang.get(laatste).isEmpty()) {
      jsonRangen.get(laatste).add(parser.parse(jsonRang.get(laatste)
                                                       .toString()));
      jsonRang.get(laatste).clear();
    }

    for (int i = rangen.size()-2; i > iRoot; i--) {
      String  rang    = rangen.get(i);
      if (!jsonRang.get(rang).isEmpty()) {
        if (!jsonRangen.get(laatste).isEmpty()) {
          jsonRang.get(rang).put(NatuurTools.KEY_SUBRANGEN,
                                 parser.parse(jsonRangen.get(laatste)
                                                        .toString()));
          jsonRangen.get(laatste).clear();
        }
        jsonRangen.get(rang).add(parser.parse(jsonRang.get(rang)
                                                      .toString()));
        jsonRang.get(rang).clear();
        laatste = rang;
      } else {
        if (!jsonRangen.get(rang).isEmpty()) {
          if (!jsonRangen.get(laatste).isEmpty()) {
            for (Object subrang : (JSONArray) jsonRangen.get(laatste)) {
              jsonRangen.get(rang).add(parser.parse(subrang.toString()));
            }
            jsonRangen.get(laatste).clear();
          }
          laatste = rang;
        }
      }
    }

    if (!jsonRang.get(root).isEmpty()) {
      jsonRang.get(root).put(NatuurTools.KEY_SUBRANGEN,
                             parser.parse(jsonRangen.get(laatste).toString()));
    } else {
      for (Object subrang : (JSONArray) jsonRangen.get(laatste)) {
        jsonRangen.get(root).add(parser.parse(subrang.toString()));
      }
    }
    jsonRangen.get(laatste).clear();
  }

  private static boolean setParameters(String[] args) {
    Arguments     arguments = new Arguments(args);
    List<String>  fouten    = new ArrayList<>();

    arguments.setParameters(new String[] {PAR_CHARSETIN,
                                          PAR_CHARSETUIT,
                                          PAR_CSVBESTAND,
                                          NatuurTools.PAR_DBURL,
                                          NatuurTools.PAR_DBUSER,
                                          PAR_INVOERDIR,
                                          PAR_JSONBESTAND,
                                          PAR_TAAL,
                                          NatuurTools.PAR_TAXAROOT,
                                          PAR_UITVOERDIR,
                                          NatuurTools.PAR_WACHTWOORD});
    arguments.setVerplicht(new String[] {PAR_CSVBESTAND,
                                         NatuurTools.PAR_DBURL,
                                         NatuurTools.PAR_DBUSER,
                                         NatuurTools.PAR_TAXAROOT});
    if (!arguments.isValid()) {
      fouten.add(getMelding(ERR_INVALIDPARAMS));
    }

    parameters  = new HashMap<>();

    setParameter(arguments, PAR_CHARSETIN, Charset.defaultCharset().name());
    setParameter(arguments, PAR_CHARSETUIT, Charset.defaultCharset().name());
    setBestandParameter(arguments, PAR_CSVBESTAND, EXT_CSV);
    setParameter(arguments, NatuurTools.PAR_DBURL);
    setParameter(arguments, NatuurTools.PAR_DBUSER);
    setDirParameter(arguments, PAR_INVOERDIR);
    setBestandParameter(arguments, PAR_JSONBESTAND, EXT_JSON);
    if (!hasParameter(PAR_JSONBESTAND)) {
      setParameter(PAR_JSONBESTAND, getParameter(PAR_CSVBESTAND));
    }
    setParameter(arguments, PAR_TAAL, Locale.getDefault().getLanguage());
    setParameter(arguments, NatuurTools.PAR_TAXAROOT);
    setDirParameter(arguments, PAR_UITVOERDIR, getParameter(PAR_INVOERDIR));
    setParameter(arguments, NatuurTools.PAR_WACHTWOORD);

    if (DoosUtils.nullToEmpty(parameters.get(PAR_CSVBESTAND))
                 .contains(File.separator)) {
      fouten.add(
          MessageFormat.format(
              getMelding(ERR_BEVATDIRECTORY), PAR_CSVBESTAND));
    }
    if (DoosUtils.nullToEmpty(parameters.get(PAR_JSONBESTAND))
                 .contains(File.separator)) {
      fouten.add(
          MessageFormat.format(
              getMelding(ERR_BEVATDIRECTORY), PAR_JSONBESTAND));
    }

    if (fouten.isEmpty()) {
      return true;
    }

    help();
    printFouten(fouten);

    return false;
  }

  private static void verwerkCsv(CsvBestand csvBestand)
      throws BestandException, ParseException {
    while (csvBestand.hasNext()) {
      lijnen++;
      String[]  veld          = csvBestand.next();
      String    rang          = veld[0];
      String    latijnsenaam  = veld[1];
      String    naam          = veld[2];

      verwerkTaxon(rang, latijnsenaam, naam);
    }
  }

  private static void verwerkTaxon(String rang, String latijnsenaam,
                                   String naam)
      throws ParseException {
    JSONObject  namen       = new JSONObject();
    Integer     volgnummer  = getVolgnummer(rang);

    controleerHierarchie(rang, latijnsenaam);

    if (!jsonRang.get(rang).isEmpty()) {
      jsonRangen.get(rang).add(parser.parse(jsonRang.get(rang).toString()));
      jsonRang.get(rang).clear();
    }

    if (DoosUtils.isNotBlankOrNull(naam)) {
      namen.put(taal, naam);
    }
    jsonRang.get(rang).put(NatuurTools.KEY_LATIJN, latijnsenaam);
    if (!namen.isEmpty()) {
      jsonRang.get(rang).put(NatuurTools.KEY_NAMEN,
                             parser.parse(namen.toString()));
    }
    jsonRang.get(rang).put(NatuurTools.KEY_RANG, rang);
    jsonRang.get(rang).put(NatuurTools.KEY_SEQ, volgnummer);
  }
}