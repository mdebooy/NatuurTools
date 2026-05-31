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

import eu.debooy.doosutils.Batchjob;
import eu.debooy.doosutils.DoosBanner;
import eu.debooy.doosutils.DoosConstants;
import eu.debooy.doosutils.DoosUtils;
import eu.debooy.doosutils.ParameterBundle;
import eu.debooy.doosutils.access.JsonBestand;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.doosutils.percistence.DbConnection;
import eu.debooy.natuur.NatuurConstants;
import eu.debooy.natuur.domain.DetailDto;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


/**
 * @author Marco de Booij
 */
public class JsonCheck extends Batchjob {
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  private static final  String  QUERY =
      "select d from DetailDto d where d.parentLatijnsenaam='%s' "
          + "and d.rang='%s'";

  protected static  Map<String, String> db            = new TreeMap<>();
  protected static  Map<String, String> json          = new TreeMap<>();
  protected static  Set<String>         rangen        = new TreeSet<>();
  protected static  String              rootLatijnsenaam;
  protected static  String              rootNaam;
  protected static  String              rootRang;
  protected static  String              taal          =
      NatuurConstants.DEF_TAAL;

  protected JsonCheck() {}

  public static void execute(String[] args) {
    setParameterBundle(
        new ParameterBundle.Builder()
                           .setArgs(args)
                           .setBanner(new DoosBanner())
                           .setBaseName(NatuurTools.TOOL_JSONCHECK)
                           .build());

    if (!paramBundle.isValid()) {
      return;
    }

    init();

    laadJsonbestand();
    laadDatabase();

    var nieuw     = taxonNieuw();
    var onbekend  = taxonOnbekend();

    DoosUtils.naarScherm();
    DoosUtils.naarScherm(
        MessageFormat.format(
            resourceBundle.getString(NatuurTools.MSG_AANTALNIEUW), nieuw));
    DoosUtils.naarScherm(
        MessageFormat.format(
            resourceBundle.getString(NatuurTools.MSG_AANTALONBEKEND),
            onbekend));
    klaar();
  }

  private static String getNaam(Object namen) {
    if (!(namen instanceof JSONObject)) {
      return "";
    }

    if (((JSONObject) namen).containsKey(taal)) {
      return ((JSONObject) namen).get(taal).toString();
    }

    return "";
  }

  private static void init() {
    if (paramBundle.containsArgument(NatuurTools.PAR_RANGEN)) {
      rangen.addAll(Arrays.asList(paramBundle.getString(NatuurTools.PAR_RANGEN)
                                             .split(",")));
    }

    taal  = paramBundle.getString(PAR_TAAL);
  }

  private static void laadDatabase() {
    try (var dbConn =
        new DbConnection.Builder()
              .setDbUser(paramBundle.getString(NatuurTools.PAR_DBUSER))
              .setDbUrl(paramBundle.getString(NatuurTools.PAR_DBURL))
              .setWachtwoord(paramBundle.getString(NatuurTools.PAR_WACHTWOORD))
              .setPersistenceUnitName(NatuurTools.EM_UNITNAME)
              .build()) {
      var em  = dbConn.getEntityManager();

      for (var rang : rangen) {
        for (var detail : em.createQuery(String.format(
                                            QUERY,
                                            rootLatijnsenaam, rang))
                            .getResultList()) {
          var detailDto = (DetailDto) detail;
          db.put(detailDto.getLatijnsenaam(),
                 String.format("%s,%s", detailDto.getRang(),
                                        detailDto.getNaam(taal)));
        }
      }
    } catch (Exception e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }
  }

  private static void laadJsonbestand() {
    try (var jsonBestand =
          new JsonBestand.Builder()
                         .setBestand(
                            paramBundle.getBestand(NatuurTools.PAR_JSON,
                                                   DoosConstants.EXT_JSON))
                         .build()) {
      rootLatijnsenaam  = jsonBestand.get(NatuurTools.KEY_LATIJN).toString();
      rootNaam          =
          getNaam(jsonBestand.get(NatuurTools.KEY_NAMEN));
      rootRang          = jsonBestand.get(NatuurTools.KEY_RANG).toString();

      for (Object taxa :
              (JSONArray) jsonBestand.get(NatuurTools.KEY_SUBRANGEN)) {
        laadTree((JSONObject) taxa);
      }
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }
  }

  private static void laadTree(JSONObject tree) {
    var boom  = tree.keySet();
    var rang  = tree.get(NatuurTools.KEY_RANG).toString();

    if (!paramBundle.containsArgument(NatuurTools.PAR_RANGEN)
        || rangen.contains(rang)) {
      String  naam  = "";
      if (tree.containsKey(NatuurTools.KEY_NAMEN)) {
        naam  = getNaam(tree.get(NatuurTools.KEY_NAMEN));
      }
      json.put(tree.get(NatuurTools.KEY_LATIJN).toString(),
               String.format("%s,%s", rang, naam));
      if (!rangen.contains(rang)) {
        rangen.add(rang);
      }
    }

    if (boom.contains(NatuurTools.KEY_SUBRANGEN)) {
      ((JSONArray) tree.get(NatuurTools.KEY_SUBRANGEN)).forEach(tak ->
          laadTree((JSONObject) tak));
    }
  }

  private static int taxonNieuw() {
    var nieuw = 0;

    DoosUtils.naarScherm();
    DoosUtils.naarScherm(
        resourceBundle.getString(NatuurTools.MSG_TAXANIEUW));

    for (Map.Entry<String, String> entry : json.entrySet()) {
      if (!db.containsKey(entry.getKey())) {
        DoosUtils
            .naarScherm(String.format("%s - %s", entry.getKey(),
                                                 entry.getValue()
                                                      .replace(",", " - ")));
        nieuw++;
      }
    }

    return nieuw;
  }

  private static int taxonOnbekend() {
    var onbekend  = 0;

    DoosUtils.naarScherm();
    DoosUtils.naarScherm(
        resourceBundle.getString(NatuurTools.MSG_TAXAONBEKEND));

    for (Map.Entry<String, String> entry : db.entrySet()) {
      if (!json.containsKey(entry.getKey())) {
        DoosUtils
            .naarScherm(String.format("%s - %s", entry.getKey(),
                                                 entry.getValue()
                                                      .replace(",", " - ")));
        onbekend++;
      }
    }

    return onbekend;
  }
}
