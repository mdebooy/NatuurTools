/*
 * Copyright (c) 2025 Marco de Booij
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
import eu.debooy.natuur.domain.TaxonDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;
import org.apache.commons.lang3.ArrayUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


/**
 * @author Marco de Booij
 */
public class AviListData extends Batchjob {
  protected static final  String  ERR_KOLOM = "error.geen.kolom";

  protected static final  String  RANG_FAMILIE    = "family";
  protected static final  String  RANG_GENUS      = "genus";
  protected static final  String  RANG_ORDE       = "order";
  protected static final  String  RANG_SPECIES    = "species";
  protected static final  String  RANG_SUBSPECIES = "subspecies";

  private static final  String  ONBEKEND  = "ONBEKEND";

  private static  final Map<String, Map<String, String>>
                                        cache           = new HashMap<>();
  private static final  List<String>    exclusief       = new ArrayList<>();
  private static final  JSONObject      familie         = new JSONObject();
  private static final  JSONArray       families        = new JSONArray();
  private static final  JSONObject      geslacht        = new JSONObject();
  private static final  JSONArray       geslachten      = new JSONArray();
  private static final  List<String>    metInitCap      =
      Arrays.asList("nld");
  private static final  JSONObject      ondersoort      = new JSONObject();
  private static final  JSONArray       ondersoorten    = new JSONArray();
  private static final  JSONObject      orde            = new JSONObject();
  private static final  JSONArray       ordes           = new JSONArray();
  private static final  List<String>    osoteksten      =
      Arrays.asList("ssp.", "亜種", "підвид");
  private static final  JSONParser      parser          = new JSONParser();
  private static final  List<String>    rangen          = new ArrayList<>();
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());
  private static final  JSONObject      soort           = new JSONObject();
  private static final  JSONArray       soorten         = new JSONArray();
  private static final  Map<String, String>
                                        talen           = new HashMap();
  private static final  List<String>    teVerwijderen   =
      Arrays.asList("(nominate-groep)", "(sensu stricto)", "(sensu lato)");
  private static final  Map<String, Integer>
                                        totalen         = new HashMap<>();
  private static final  String[]        veldenTaxa      =
      new String[] {"Sequence", "Taxon_rank", "Family_English_name",
                    "Scientific_name", "English_name_AviList",
                    "IUCN_Red_List_Category"};
  private static final  String[]        veldenNamen      =
      new String[] {"category", "sci_name", "alternate_com_name",
                    "locale_code", "locale_name"};

  private static  Integer   factor        = NatuurConstants.VOLGNUMMERFACTOR;
  private static  int[]     kolommen;
  private static  Integer   lijnen        = 0;
  private static  boolean   metDatabase   = false;
  private static  boolean   perRang       = false;
  private static  Integer   sequence      = 0;
  private static  String    taal          = "";

  protected AviListData() {}

  static class AviListTaxon {
    private String  familienaam;
    private String  latijnsenaam;
    private String  naam;
    private String  rang;
    private String  status;
    private Long    volgnummer;

    public String getFamilienaam() {
      return familienaam;
    }

    public String getLatijnsenaam() {
      return latijnsenaam;
    }

    public String getNaam() {
      return naam;
    }

    public String getRang() {
      return rang;
    }

    public String getStatus() {
      return status;
    }

    public Long getVolgnummer() {
      return volgnummer;
    }

    public void setFamilienaam(String familienaam) {
      this.familienaam  = DoosUtils.strip(familienaam);
    }

    public void setLatijnsenaam(String latijnsenaam) {
      this.latijnsenaam = DoosUtils.strip(latijnsenaam);
    }

    public void setNaam(String naam) {
      this.naam         = DoosUtils.strip(naam);
    }

    public void setRang(String rang) {
      this.rang         = DoosUtils.strip(rang);
    }

    public void setStatus(String status) {
      this.status       = DoosUtils.stripToLowerCase(status);
    }

    public void setVolgnummer(Long volgnummer) {
      this.volgnummer   = volgnummer;
    }

    @Override
    public String toString() {
      return String.format("%20s - %2s - %s (%s)",
                            getRang(), getStatus(), getNaam(),
                            getLatijnsenaam());
    }
  }

  private static void addNaam(String iso6392t, String naam,
                              String latijnsenaam) {
    var aanwezig  = false;
    for (var oso: osoteksten) {
      aanwezig  = aanwezig ||  naam.contains(" (" + oso);
    }
    if (aanwezig) {
      return;
    }

    var woorden = latijnsenaam.split(" ");
    if (woorden.length == 3
        && naam.endsWith("(" + woorden[2] + ")")) {
      return;
    }

    for (var verwijder: teVerwijderen) {
      var start = naam.toLowerCase().indexOf(verwijder);
      if (start > -1) {
        naam  = String.format("%s%s", naam.substring(0, start),
                              naam.substring(start + verwijder.length()));
      }
    }

    if (naam.startsWith("\"")
        && naam.endsWith("\"")) {
      naam  = DoosUtils.stripBeginEnEind(naam, "\"");
    }
    if (metInitCap.contains(iso6392t)) {
      naam  = DoosUtils.initCap(naam);
    }
    if (!cache.containsKey(latijnsenaam)) {
      cache.put(latijnsenaam, new TreeMap<>());
    }

    if (!cache.get(latijnsenaam).containsKey(iso6392t)) {
      cache.get(latijnsenaam).put(iso6392t, naam);
    }
  }

  private static void addRang(String rang) {
    totalen.put(rang, totalen.get(rang)+1);
    sequence++;
  }

  private static void addVorigGeslacht() throws ParseException {
    if (geslacht.isEmpty()) {
      return;
    }

    addVorigeSoort();

    geslacht.put(NatuurTools.KEY_SUBRANGEN, parser.parse(soorten.toString()));
    geslachten.add(parser.parse(geslacht.toString()));

    geslacht.clear();
    soorten.clear();
  }

  private static void addVorigeFamilie() throws ParseException {
    if (familie.isEmpty()) {
      return;
    }

    addVorigGeslacht();

    familie.put(NatuurTools.KEY_SUBRANGEN, parser.parse(geslachten.toString()));
    families.add(parser.parse(familie.toString()));

    familie.clear();
    geslachten.clear();
  }

  private static void addVorigeOnderSoort() throws ParseException {
    if (ondersoorten.isEmpty()) {
      return;
    }

    soort.put(NatuurTools.KEY_SUBRANGEN,
              parser.parse(ondersoorten.toString()));

    ondersoorten.clear();
  }

  private static void addVorigeOrde() throws ParseException {
    if (orde.isEmpty()) {
      return;
    }

    addVorigeFamilie();

    orde.put(NatuurTools.KEY_SUBRANGEN, parser.parse(families.toString()));
    ordes.add(parser.parse(orde.toString()));

    families.clear();
    orde.clear();
  }

  private static void addVorigeSoort() throws ParseException {
    if (soort.isEmpty()) {
      return;
    }

    addVorigeOnderSoort();

    soorten.add(parser.parse(soort.toString()));

    soort.clear();
  }

  public static void execute(String[] args) {
    setParameterBundle(
        new ParameterBundle.Builder()
                           .setArgs(args)
                           .setBanner(new DoosBanner())
                           .setBaseName(NatuurTools.TOOL_AVILISTDATA)
                           .build());

    if (!paramBundle.isValid()) {
      return;
    }

    taal        = paramBundle.getString(PAR_TAAL);
    perRang     = paramBundle.getBoolean(NatuurTools.PAR_PERRANG);

    setRangen();

    if (paramBundle.containsArgument(NatuurTools.PAR_FACTOR)) {
      factor  = paramBundle.getInteger(NatuurTools.PAR_FACTOR);
    }

    init();

    var taxa    = new JSONObject();
    verwerkAviListBestand(taxa);

    NatuurTools.writeJson(paramBundle.getBestand(NatuurTools.PAR_JSON),
                          taxa, paramBundle.getString(PAR_CHARSETUIT));

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

    klaar();
  }

  private static void getIso6391(EntityManager em, String iso6391,
                                 String localeCode) {
    TaalDto taalDto;
    try {
      taalDto = (TaalDto) em.createNamedQuery(TaalDto.QRY_TAAL_ISO6391)
                            .setParameter(TaalDto.PAR_ISO6391, iso6391)
                            .getSingleResult();
    } catch (NoResultException e) {
      taalDto = null;
    }
    if (null != taalDto) {
      talen.put(localeCode, taalDto.getIso6392t());
    }
  }

  private static void getIso6392t(EntityManager em, String iso6392t,
                                  String localeCode) {
    TaalDto taalDto;
    try {
      taalDto = (TaalDto) em.createNamedQuery(TaalDto.QRY_TAAL_ISO6392T)
                            .setParameter(TaalDto.PAR_ISO6392T, iso6392t)
                            .getSingleResult();
    } catch (NoResultException e) {
      taalDto = null;
    }
    if (null != taalDto) {
      talen.put(localeCode, taalDto.getIso6392t());
    }
  }

  private static void getNaam(EntityManager em, String naam,
                              String localeCode) {
    String  localeTaal;
    if (localeCode.contains("_")) {
      localeTaal  = localeCode.split("_")[0];
    } else {
      localeTaal  = localeCode;
    }
    var naam1 = naam.split(" ")[0];

    List<TaalnaamDto> taalnamen =
        em.createNamedQuery(TaalnaamDto.QRY_METNAAM)
          .setParameter(TaalnaamDto.PAR_TAAL, taal)
          .setParameter(TaalnaamDto.PAR_NAAM, naam1).getResultList();

    if (taalnamen.size() != 1) {
      return;
    }

    TaalDto taalDto = new TaalDto();
    try {
      taalDto = em.find(TaalDto.class, taalnamen.get(0).getTaalId());
    } catch (NoResultException e) {
      // Gebruik lege TaalDto;
    }
    if (null != taalDto.getTaalId()
        && taalDto.getIso6391().equals(localeTaal)) {
      talen.put(localeCode, taalDto.getIso6392t());
    }
  }

  private static String getTaalUitLocale(String locale) {
    var deel  = locale.split("_");

    return  deel[0].equals(deel[1].toLowerCase()) ? deel[0] : locale;
  }

  private static int getVolgnummer(String rang) {
    if (!perRang) {
      return sequence;
    }

    return totalen.get(rang);
  }

  private static void init() {
    metDatabase = paramBundle.containsArgument(NatuurTools.PAR_DBURL);

    vulTalen();

    if (!metDatabase) {
      sequence  = factor
                    * paramBundle.getInteger(NatuurTools.PAR_KLASSEVOLGNUMMER);
    }

    verwerkAviListNamenBestand();
  }

  private static void nieuwGeslacht(AviListTaxon aviListTaxon)
      throws ParseException {
    addVorigGeslacht();

    setVelden(geslacht, aviListTaxon, NatuurConstants.RANG_GESLACHT);
  }

  private static void nieuweFamilie(AviListTaxon aviListTaxon)
      throws ParseException {
    addVorigeFamilie();

    setVelden(familie, aviListTaxon, NatuurConstants.RANG_FAMILIE);
  }

  private static void nieuweOnderSoort(AviListTaxon aviListTaxon)
      throws ParseException {
    setVelden(ondersoort, aviListTaxon, NatuurConstants.RANG_ONDERSOORT);

    ondersoorten.add(parser.parse(ondersoort.toString()));
    ondersoort.clear();
  }

  private static void nieuweOrde(AviListTaxon aviListTaxon)
      throws ParseException {
    addVorigeOrde();

    setVelden(orde, aviListTaxon, NatuurConstants.RANG_ORDE);
  }

  private static void nieuweSoort(AviListTaxon aviListTaxon)
      throws ParseException {
    addVorigeSoort();

    setVelden(soort, aviListTaxon, NatuurConstants.RANG_SOORT);
  }

  private static void nieuweTaxon(AviListTaxon aviListTaxon)
      throws ParseException {
    switch (aviListTaxon.getRang()) {
      case RANG_FAMILIE -> nieuweFamilie(aviListTaxon);
      case RANG_GENUS -> nieuwGeslacht(aviListTaxon);
      case RANG_ORDE -> nieuweOrde(aviListTaxon);
      case RANG_SPECIES -> nieuweSoort(aviListTaxon);
      case RANG_SUBSPECIES -> nieuweOnderSoort(aviListTaxon);
      default -> DoosUtils.foutNaarScherm(MessageFormat.format(
                      resourceBundle.getString(NatuurTools.ERR_RANGONBEKEND),
                      aviListTaxon.getRang()));
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

  private static void setVelden(JSONObject json, AviListTaxon aviListTaxon,
                                String rang) {
    addRang(rang);

    json.put(NatuurTools.KEY_LATIJN, aviListTaxon.getLatijnsenaam());
    json.put(NatuurTools.KEY_RANG, rang);
    json.put(NatuurTools.KEY_SEQ, getVolgnummer(rang));
    if (DoosUtils.isNotBlankOrNull(aviListTaxon.getStatus())) {
      json.put(NatuurTools.KEY_STATUS, aviListTaxon.getStatus());
    }
    var namen = new JSONObject();
    if (cache.containsKey(aviListTaxon.getLatijnsenaam())) {
      namen.putAll(cache.get(aviListTaxon.getLatijnsenaam()));
    }
    if (DoosUtils.isNotBlankOrNull(aviListTaxon.getNaam())) {
      namen.put(taal, aviListTaxon.getNaam());
    }
    if (!namen.isEmpty()) {
      json.put(NatuurTools.KEY_NAMEN, namen);
    }
  }

  private static void verwerkHeader(String[] header, String[] velden)
      throws BestandException {
    kolommen  = new int[velden.length];

    for (var i = 0; i < velden.length; i++) {
      kolommen[i] = ArrayUtils.indexOf(header, velden[i]);
      if (kolommen[i] == -1) {
        throw new BestandException(MessageFormat.format(
                  resourceBundle.getString(ERR_KOLOM),
                  velden[i]));
      }
    }
  }

  private static void verwerkAviListBestand(JSONObject taxa) {
    try (var csvBestand  =
          new CsvBestand.Builder()
                        .setBestand(paramBundle.getBestand(
                                NatuurTools.PAR_AVILISTBESTAND))
                        .setCharset(paramBundle.getString(PAR_CHARSETIN))
                        .setHeader(true)
                        .build()) {
      verwerkHeader(csvBestand.getKolomNamen(), veldenTaxa);

      while (csvBestand.hasNext()) {
        verwerkTaxon(csvBestand.next());
      }

      addVorigeOrde();

      taxa.put(NatuurTools.KEY_RANG, NatuurConstants.RANG_KLASSE);
      taxa.put(NatuurTools.KEY_LATIJN, NatuurConstants.LAT_VOGELS);
      taxa.put(NatuurTools.KEY_SUBRANGEN, ordes);
      taxa.put(NatuurTools.KEY_SEQ,
               paramBundle.getInteger(NatuurTools.PAR_KLASSEVOLGNUMMER));
    } catch (BestandException | ParseException e) {
      DoosUtils.foutNaarScherm(String.format("%s: %s",
              paramBundle.getBestand(NatuurTools.PAR_NAMEN),
                                             e.getLocalizedMessage()));
    }
  }

  private static void verwerkAviListNamenBestand() {
    try (var csvBestand  =
          new CsvBestand.Builder()
                        .setBestand(
                            paramBundle.getBestand(NatuurTools.PAR_NAMEN))
                        .setCharset(paramBundle.getString(PAR_CHARSETIN))
                        .setHeader(true)
                        .build()) {

      verwerkHeader(csvBestand.getKolomNamen(), veldenNamen);

      while(csvBestand.hasNext()) {
        var veld          = csvBestand.next();
        var latijnsenaam  = veld[kolommen[1]];
        var taalkode      = veld[kolommen[3]];

        if (!talen.containsKey(taalkode)) {
          continue;
        }

        var naam          = veld[kolommen[2]];
        var iso6392t      = talen.get(taalkode);

        addNaam(iso6392t, naam, latijnsenaam);
      }
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(String.format("%s: %s",
              paramBundle.getBestand(NatuurTools.PAR_NAMEN),
                                             e.getLocalizedMessage()));
    }
  }

  private static void verwerkTaxon(String[] taxon) {
    var aviListTaxon  = new AviListTaxon();

    aviListTaxon.setVolgnummer(Long.valueOf(taxon[kolommen[0]]));
    aviListTaxon.setRang(taxon[kolommen[1]]);
    aviListTaxon.setFamilienaam(taxon[kolommen[2]]);
    aviListTaxon.setLatijnsenaam(taxon[kolommen[3]]);
    aviListTaxon.setNaam(taxon[kolommen[4]]);
    aviListTaxon.setStatus(taxon[kolommen[5]].split(" ")[0].toLowerCase());
    DoosUtils.naarScherm(aviListTaxon.toString());

    try {
      nieuweTaxon(aviListTaxon);
    } catch (ParseException e) {
      DoosUtils.foutNaarScherm(String.format("%s %s  - %s",
                                             aviListTaxon.getRang(),
                                             aviListTaxon.getLatijnsenaam(),
                                             e.getLocalizedMessage()));
    }

    lijnen++;
  }

  private static void vulTalen() {
    if (paramBundle.containsArgument(NatuurTools.PAR_EXCLUSIEF)) {
      exclusief.addAll(
              Arrays.asList(paramBundle.getString(NatuurTools.PAR_EXCLUSIEF)
                                       .split(",")));
    }
    if (paramBundle.containsArgument(NatuurTools.PAR_TALEN)) {
      vulTalenUitParameter();
    }

    if (metDatabase) {
      vulUitDatabase();
    }

    if (paramBundle.containsArgument(NatuurTools.PAR_EXCLUSIEF)) {
      talen.entrySet().removeIf(t -> exclusief.contains(t.getKey()));
    }

    talen.entrySet().removeIf(t -> t.getValue().equals(ONBEKEND));
  }

  private static void vulTalenUitBestand(EntityManager em) {
    try (var csvBestand  =
          new CsvBestand.Builder()
                        .setBestand(
                            paramBundle.getBestand(NatuurTools.PAR_NAMEN))
                        .setCharset(paramBundle.getString(PAR_CHARSETIN))
                        .setHeader(true)
                        .build()) {

      verwerkHeader(csvBestand.getKolomNamen(), veldenNamen);

      while(csvBestand.hasNext()) {
        var veld      = csvBestand.next();

        var localeCode  = veld[kolommen[3]];
        var naam        = veld[kolommen[4]];
        var taalkode    = veld[kolommen[3]];

        if (taalkode.contains("_")) {
          taalkode  = getTaalUitLocale(taalkode);
        }

        if (talen.containsKey(localeCode)
            || DoosUtils.isBlankOrNull(taalkode)
            || talen.containsKey(taalkode)
            || exclusief.contains(localeCode)) {
          continue;
        }

        talen.put(veld[kolommen[3]], ONBEKEND);
        switch (taalkode.length()) {
          case 2 -> getIso6391(em, taalkode, localeCode);
          case 3 -> getIso6392t(em, taalkode, localeCode);
          default -> getNaam(em, naam, localeCode);
        }
        if (talen.get(localeCode).equals(ONBEKEND)) {
          DoosUtils.foutNaarScherm(
              MessageFormat.format(
                  resourceBundle.getString(NatuurTools.MSG_TAALONBEKEND),
                  localeCode, naam));
        }
      }
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(String.format("%s: %s",
              paramBundle.getBestand(NatuurTools.PAR_NAMEN),
                                             e.getLocalizedMessage()));
    }
  }

  private static void vulTalenUitParameter() {
    var taalkodes = paramBundle.getString(NatuurTools.PAR_TALEN).split(",");

    for (var taalkode: taalkodes) {
      if (taalkode.contains("=")) {
        var taalsplit = taalkode.split("=");
        talen.put(taalsplit[0], taalsplit[1]);
      } else {
        DoosUtils.foutNaarScherm("parameter talen is niet compleet: " + taalkode);
      }
    }
  }

  private static void vulUitDatabase() {
    try (var dbConn =
        new DbConnection.Builder()
              .setDbUser(paramBundle.getString(NatuurTools.PAR_DBUSER))
              .setDbUrl(paramBundle.getString(NatuurTools.PAR_DBURL))
              .setWachtwoord(paramBundle.getString(NatuurTools.PAR_WACHTWOORD))
              .setPersistenceUnitName(NatuurTools.EM_UNITNAME)
              .build()) {
      var em      = dbConn.getEntityManager();
      var query   = em.createNamedQuery(TaxonDto.QRY_LATIJNSENAAM);
      query.setParameter(TaxonDto.PAR_LATIJNSENAAM,
                         NatuurConstants.LAT_VOGELS);
      var klasse  = (TaxonDto) query.getSingleResult();
      sequence    = factor * klasse.getVolgnummer().intValue();

      vulTalenUitBestand(em);
    } catch (Exception e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }
  }
}
