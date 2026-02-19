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

import eu.debooy.doosutils.Batchjob;
import eu.debooy.doosutils.DoosBanner;
import eu.debooy.doosutils.DoosUtils;
import eu.debooy.doosutils.IBanner;
import eu.debooy.doosutils.ParameterBundle;
import eu.debooy.doosutils.access.JsonBestand;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.natuur.domain.TaxonDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import org.json.simple.JSONObject;


/**
 * @author Marco de Booij
 */
public class NatuurTools extends Batchjob {
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  private static final  IBanner banner  = new DoosBanner();

  protected static final  String  EM_UNITNAME = "natuur";

  protected static final  String  ERR_RANGONBEKEND  = "error.rang.onbekend";

  protected static final  String  KEY_LATIJN        = "latijn";
  protected static final  String  KEY_NAMEN         = "namen";
  protected static final  String  KEY_RANG          = "rang";
  protected static final  String  KEY_SEQ           = "seq";
  protected static final  String  KEY_SOORTEN       = "soorten";
  protected static final  String  KEY_STATUS        = "status";
  protected static final  String  KEY_SUBRANGEN     = "subrangen";
  protected static final  String  KEY_TAXA          = "taxa";

  protected static final  String  LBL_AANTAL          = "label.aantal";
  protected static final  String  LBL_NIEUW           = "label.nieuw";
  protected static final  String  LBL_RANGEN          = "label.rangen";
  protected static final  String  LBL_SOORTENONBEKEND = "label.soortenonbekend";
  protected static final  String  LBL_TALEN           = "label.talen";
  protected static final  String  LBL_UPDATE          = "label.gewijzigd";

  protected static final  String  MSG_AANMAKEN        = "msg.aanmaken";
  protected static final  String  MSG_AANTALNIEUW     = "msg.aantalnieuw";
  protected static final  String  MSG_AANTALONBEKEND  = "msg.aantalonbekend";
  protected static final  String  MSG_AANTALSOORTEN   = "msg.aantalsoorten";
  protected static final  String  MSG_AFWEZIG         = "msg.afwezig";
  protected static final  String  MSG_BESTAANTAL      = "msg.bestaat.al";
  protected static final  String  MSG_BESTAANTNIET    = "msg.bestaat.niet";
  protected static final  String  MSG_GELEZEN         = "msg.gelezen";
  protected static final  String  MSG_HERNOEM         = "msg.hernoem";
  protected static final  String  MSG_HERNUMMER       = "msg.hernummer";
  protected static final  String  MSG_HIERARCHIE      = "msg.hierarchie";
  protected static final  String  MSG_LIJNEN          = "msg.lijnen";
  protected static final  String  MSG_METONDERSOORTEN = "msg.metondersoorten";
  protected static final  String  MSG_NIEUW           = "msg.nieuw";
  protected static final  String  MSG_ONBEKEND        = "msg.onbekend";
  protected static final  String  MSG_SKIPSTRUCTUUR   = "msg.skipstructuur";
  protected static final  String  MSG_TAALONBEKEND    = "msg.taal.onbekend";
  protected static final  String  MSG_TAALONJUIST     = "msg.taal.onjuist";
  protected static final  String  MSG_TALEN           = "msg.talen";
  protected static final  String  MSG_TAXANIEUW       = "msg.taxanieuw";
  protected static final  String  MSG_TAXAONBEKEND    = "msg.taxaonbekend";
  protected static final  String  MSG_UITVOER         = "msg.uitvoer";
  protected static final  String  MSG_UPDATED         = "msg.updated";
  protected static final  String  MSG_VERSCHIL        = "msg.verschil";
  protected static final  String  MSG_WIJZIGEN        = "msg.wijzigen";
  protected static final  String  MSG_WIJZIGING       = "msg.wijziging";

  protected static final  String  PAR_AANMAAK           = "aanmaak";
  protected static final  String  PAR_AVILISTBESTAND    = "avilistbestand";
  protected static final  String  PAR_AUTEUR            = "auteur";
  protected static final  String  PAR_BEHOUD            = "behoud";
  protected static final  String  PAR_EXCLUSIEF         = "exclusief";
  protected static final  String  PAR_IOCNAMEN          = "iocnamen";
  protected static final  String  PAR_IOCSTRUCTUUR      = "iocstructuur";
  protected static final  String  PAR_DBURL             = "dburl";
  protected static final  String  PAR_DBUSER            = "dbuser";
  protected static final  String  PAR_FACTOR            = "factor";
  protected static final  String  PAR_HERNUMMER         = "hernummer";
  protected static final  String  PAR_JSON              = "json";
  protected static final  String  PAR_KLASSEVOLGNUMMER  = "klassevolgnummer";
  protected static final  String  PAR_KLEUR             = "kleur";
  protected static final  String  PAR_LOGGING           = "logging";
  protected static final  String  PAR_MDDBESTAND        = "mddbestand";
  protected static final  String  PAR_METONDERSOORT     = "metondersoort";
  protected static final  String  PAR_NAMEN             = "namen";
  protected static final  String  PAR_PERRANG           = "perrang";
  protected static final  String  PAR_RANGEN            = "rangen";
  protected static final  String  PAR_SUBTITEL          = "subtitel";
  protected static final  String  PAR_STIL              = "stil";
  protected static final  String  PAR_TALEN             = "talen";
  protected static final  String  PAR_TAXAROOT          = "taxaroot";
  protected static final  String  PAR_TEMPLATE          = "template";
  protected static final  String  PAR_TITEL             = "titel";
  protected static final  String  PAR_WACHTWOORD        = "wachtwoord";

  protected static final  String  QRY_RANG  =
      "select r from RangDto r order by r.niveau";

  protected static final  String  TOOL_AVILISTDATA  = "avilistdata";
  protected static final  String  TOOL_CSVNAARJSON  = "csvnaarjson";
  protected static final  String  TOOL_DBNAARJSON   = "dbnaarjson";
  protected static final  String  TOOL_HERNOEM      = "hernoem";
  protected static final  String  TOOL_IOCDATA      = "iocdata";
  protected static final  String  TOOL_JSONCHECK    = "jsoncheck";
  protected static final  String  TOOL_MDDDATA      = "mdddata";
  protected static final  String  TOOL_TAXAIMPORT   = "taxaimport";
  protected static final  String  TOOL_TAXONOMIE    = "taxonomie";

  protected static final  String  TXT_BANNER  = "help.natuurtools";

  protected static final  List<String>  tools =
      Arrays.asList(TOOL_AVILISTDATA, TOOL_CSVNAARJSON, TOOL_DBNAARJSON,
                    TOOL_HERNOEM, TOOL_IOCDATA, TOOL_JSONCHECK, TOOL_MDDDATA,
                    TOOL_TAXAIMPORT, TOOL_TAXONOMIE);

  protected NatuurTools() {}

  protected static TaxonDto getTaxon(String latijnsenaam, EntityManager em) {
    var query = em.createNamedQuery(TaxonDto.QRY_LATIJNSENAAM);
    query.setParameter(TaxonDto.PAR_LATIJNSENAAM, latijnsenaam);
    TaxonDto  resultaat;
    try {
      resultaat = (TaxonDto) query.getSingleResult();
    } catch (NoResultException e) {
      resultaat = new TaxonDto();
    }

    return resultaat;
  }

  public static void help() {
    tools.forEach(tool -> {
      var parameterBundle = new ParameterBundle.Builder()
                                               .setBaseName(tool)
                                               .build();
      parameterBundle.help();
      DoosUtils.naarScherm(DoosUtils.stringMetLengte("_", 80, "_"));
      DoosUtils.naarScherm();
    });
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      banner.print(resourceBundle.getString(TXT_BANNER));
      help();
      return;
    }

    var commando      = args[0];
    var commandoArgs  = new String[args.length-1];
    System.arraycopy(args, 1, commandoArgs, 0, args.length-1);

    switch (commando.toLowerCase()) {
      case TOOL_AVILISTDATA:
        AviListData.execute(commandoArgs);
        break;
      case TOOL_CSVNAARJSON:
        CsvNaarJson.execute(commandoArgs);
        break;
      case TOOL_DBNAARJSON:
        DbNaarJson.execute(commandoArgs);
        break;
      case TOOL_HERNOEM:
        Hernoem.execute(commandoArgs);
        break;
      case TOOL_IOCDATA:
        IocData.execute(commandoArgs);
        break;
      case TOOL_JSONCHECK:
        JsonCheck.execute(commandoArgs);
        break;
      case TOOL_MDDDATA:
        MddData.execute(commandoArgs);
        break;
      case TOOL_TAXAIMPORT:
        TaxaImport.execute(commandoArgs);
        break;
      case TOOL_TAXONOMIE:
        Taxonomie.execute(commandoArgs);
        break;
      default:
        banner.print(resourceBundle.getString(TXT_BANNER));
        help();
        DoosUtils.foutNaarScherm(
            MessageFormat.format(getMelding(ERR_TOOLONBEKEND), commando));
        DoosUtils.naarScherm();
        break;
    }
  }

  protected static void printRangtotalen(List<String> rangen,
                                         Map<String, Integer> totalen) {
    if (rangen.isEmpty()) {
      return;
    }

    DoosUtils.naarScherm();
    rangen.stream()
          .filter(rang -> totalen.get(rang) > 0)
          .forEachOrdered(rang ->
        DoosUtils.naarScherm(String.format("%7s: %,6d",
                                           rang, totalen.get(rang))));
  }

  protected static void printTotalen(String header, List<String> volgorde,
                                         Map<String, Totalen> totalen) {
    if (volgorde.isEmpty()) {
      return;
    }

    DoosUtils.naarScherm();
    DoosUtils.naarScherm(header);
    DoosUtils.naarScherm(DoosUtils.stringMetLengte("-", header.length(), "-"));
    for (var element : volgorde) {
      if (totalen.containsKey(element)) {
        var totaal  = totalen.get(element);
        if (totaal.getAantal() > 0) {
          DoosUtils.naarScherm(totalen.get(element).toString());
        }
      }
    }
  }

  protected static void writeJson(String bestand, JSONObject taxa,
                                  String charset) {
    try (var jsonBestand  =
          new JsonBestand.Builder().setBestand(bestand)
                                   .setCharset(charset)
                                   .setLezen(false)
                                   .setPrettify(true).build()) {
      jsonBestand.write(taxa);
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }
  }
}
