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

import eu.debooy.doosutils.Arguments;
import eu.debooy.doosutils.Banner;
import eu.debooy.doosutils.Batchjob;
import eu.debooy.doosutils.DoosUtils;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;


/**
 * @author Marco de Booij
 */
public class NatuurTools extends Batchjob {
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  protected static final  String  HLP_AANMAAK       = "help.aanmaak";
  protected static final  String  HLP_ENKELNAMEN    = "help.enkelnamen";
  protected static final  String  HLP_DBURL         = "help.dburl";
  protected static final  String  HLP_DBUSER        = "help.dbuser";
  protected static final  String  HLP_HERNUMMER     = "help.hernummer";
  protected static final  String  HLP_INCLUDETALEN  = "help.include.talen";
  protected static final  String  HLP_IOCBESTAND    = "help.iocbestand";
  protected static final  String  HLP_IOCJSON       = "help.iocjson";
  protected static final  String  HLP_JSONBESTAND   = "help.jsonbestand";
  protected static final  String  HLP_SKIPSTRUCTUUR = "help.skipstructuur";
  protected static final  String  HLP_READONLY      = "help.readonly";
  protected static final  String  HLP_TALEN         = "help.talen";
  protected static final  String  HLP_TAXAROOT      = "help.taxaroot";

  protected static final  String  KEY_LATIJN    = "latijn";
  protected static final  String  KEY_NAMEN     = "namen";
  protected static final  String  KEY_RANG      = "rang";
  protected static final  String  KEY_SEQ       = "seq";
  protected static final  String  KEY_SUBRANGEN = "subrangen";

  protected static final  String  LBL_JSONBESTAND = "label.jsonbestand";
  protected static final  String  LBL_DBURL       = "label.dburl";
  protected static final  String  LBL_DBUSER      = "label.dbuser";
  protected static final  String  LBL_ENKELNAMEN  = "label.enkelnamen";
  protected static final  String  LBL_IOCBESTAND  = "label.iocbestand";
  protected static final  String  LBL_TALEN       = "label.talen";
  protected static final  String  LBL_TAXAROOT    = "label.taxaroot";
  protected static final  String  LBL_WACHTWOORD  = "label.wachtwoord";

  protected static final  String  MSG_AANMAKEN    = "msg.aanmaken";
  protected static final  String  MSG_FAMILIES    = "msg.families";
  protected static final  String  MSG_GESLACHTEN  = "msg.geslachten";
  protected static final  String  MSG_HERNUMMER   = "msg.hernummer";
  protected static final  String  MSG_HIERARCHIE  = "msg.hierarchie";
  protected static final  String  MSG_LIJNEN      = "msg.lijnen";
  protected static final  String  MSG_NIEUW       = "msg.nieuw";
  protected static final  String  MSG_ONBEKEND    = "msg.onbekend";
  protected static final  String  MSG_ORDES       = "msg.ordes";
  protected static final  String  MSG_SOORTEN     = "msg.soorten";
  protected static final  String  MSG_VERSCHIL    = "msg.verschil";
  protected static final  String  MSG_WIJZIGEN    = "msg.wijzigen";
  protected static final  String  MSG_WIJZIGING   = "msg.wijziging";

  protected static final  String  PAR_AANMAAK       = "aanmaken";
  protected static final  String  PAR_IOCBESTAND    = "iocbestand";
  protected static final  String  PAR_DBURL         = "dburl";
  protected static final  String  PAR_DBUSER        = "dbuser";
  protected static final  String  PAR_ENKELNAMEN    = "enkelnamen";
  protected static final  String  PAR_HERNUMMER     = "hernummer";
  protected static final  String  PAR_SKIPSTRUCTUUR = "skipstructuur";
  protected static final  String  PAR_TALEN         = "talen";
  protected static final  String  PAR_TAXAROOT      = "taxaroot";

  private NatuurTools() {}

  public static void help() {
    DoosUtils.naarScherm("  IOCNamen       ",
                         resourceBundle.getString("help.iocnamen"), 80);
    DoosUtils.naarScherm("  ImporteerLijst ",
                         resourceBundle.getString("help.importeerlijst"), 80);
    DoosUtils.naarScherm("  TaxaImport     ",
                         resourceBundle.getString("help.taxaimport"),
                         80);
    DoosUtils.naarScherm();
    IocNamen.help();
    DoosUtils.naarScherm();
    ImporteerLijst.help();
    DoosUtils.naarScherm();
    TaxaImport.help();
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      Banner.printDoosBanner("Natuur Tools");
      help();
      return;
    }

    String    commando      = args[0];

    String[]  commandoArgs  = new String[args.length-1];
    System.arraycopy(args, 1, commandoArgs, 0, args.length-1);

    if ("iocnamen".equalsIgnoreCase(commando)) {
      IocNamen.execute(commandoArgs);
      return;
    }
    if ("importeerlijst".equalsIgnoreCase(commando)) {
      ImporteerLijst.execute(commandoArgs);
      return;
    }
    if ("taxaimport".equalsIgnoreCase(commando)) {
      TaxaImport.execute(commandoArgs);
      return;
    }

    Banner.printDoosBanner("Natuur Tools");
    help();
    DoosUtils.foutNaarScherm(getMelding(ERR_TOOLONBEKEND));
  }

  protected static void setTalenParameter(Arguments arguments,
                                          Map<String, String> parameters) {
    if (arguments.hasArgument(NatuurTools.PAR_TALEN)) {
      parameters.put(NatuurTools.PAR_TALEN,
                     arguments.getArgument(NatuurTools.PAR_TALEN)
                              .toLowerCase()
                              .replaceAll("[^a-z,]", ""));
    }
  }
}
