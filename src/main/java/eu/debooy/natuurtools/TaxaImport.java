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
import eu.debooy.doosutils.Batchjob;
import eu.debooy.doosutils.DoosBanner;
import eu.debooy.doosutils.DoosUtils;
import eu.debooy.doosutils.ParameterBundle;
import eu.debooy.doosutils.access.BestandConstants;
import eu.debooy.doosutils.access.JsonBestand;
import eu.debooy.doosutils.access.TekstBestand;
import eu.debooy.doosutils.components.Message;
import eu.debooy.doosutils.errorhandling.exception.ObjectNotFoundException;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.doosutils.percistence.DbConnection;
import eu.debooy.natuur.NatuurConstants;
import eu.debooy.natuur.domain.RangDto;
import eu.debooy.natuur.domain.TaxonDto;
import eu.debooy.natuur.domain.TaxonnaamDto;
import eu.debooy.natuur.form.Taxon;
import eu.debooy.natuur.validator.TaxonValidator;
import eu.debooy.natuur.validator.TaxonnaamValidator;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


/**
 * @author Marco de Booij
 */
public class TaxaImport extends Batchjob {
  private static final  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  protected static final  Long    ONBEKEND      = -1L;
  protected static final  String  ERR_STRUCTUUR = "error.structuur";
  protected static final  String  WORDT         = " -> ";

  protected static final  String  QRY_TALEN =
      "select distinct t.taal from natuur.taxonnamen t";
  protected static final  String  QRY_TAXON =
      "select t from TaxonDto t where t.taxonId=%d";

  private static final  Map<String, Totalen>  namen     = new HashMap<>();
  private static final  Map<String, String>   prefix    = new HashMap<>();
  private static final  List<String>          rangen    = new ArrayList<>();
  private static final  List<String>          talen     = new ArrayList<>();
  private static final  Map<String, Totalen>  totalen   = new HashMap<>();

  private static  boolean       aanmaak         = false;
  private static  EntityManager em;
  private static  boolean       hernummer       = false;
  private static  String        iso6392t;
  private static  TekstBestand  log             = null;
  private static  boolean       metondersoorten = false;
  private static  boolean       readonly        = false;
  private static  boolean       stil            = false;
  private static  boolean       talenParameter  = false;

  protected TaxaImport() {}

  public static void execute(String[] args) {
    setParameterBundle(
        new ParameterBundle.Builder()
                           .setArgs(args)
                           .setBanner(new DoosBanner())
                           .setBaseName(NatuurTools.TOOL_TAXAIMPORT)
                           .build());

    if (!paramBundle.isValid()) {
      return;
    }

    if (paramBundle.containsParameter(NatuurTools.PAR_TALEN)) {
      talen.addAll(Arrays.asList(paramBundle.getString(NatuurTools.PAR_TALEN)
                                            .split(",")));
      talenParameter  = true;
    }

    String  latijnsenaam;

    if (paramBundle.containsArgument(NatuurTools.PAR_LOGGING)) {
      try {
        log =  new TekstBestand.Builder()
                .setBestand(
                        paramBundle.getBestand(NatuurTools.PAR_LOGGING))
                .setCharset(paramBundle.getString(PAR_CHARSETIN))
                .setLezen(false)
                .build();
      } catch (BestandException e) {
        DoosUtils.foutNaarScherm(e.getLocalizedMessage());
        return;
      }
    }

    try (var dbConn =
        new DbConnection.Builder()
              .setDbUser(paramBundle.getString(NatuurTools.PAR_DBUSER))
              .setDbUrl(paramBundle.getString(NatuurTools.PAR_DBURL))
              .setWachtwoord(paramBundle.getString(NatuurTools.PAR_WACHTWOORD))
              .setPersistenceUnitName(NatuurTools.EM_UNITNAME)
              .build()) {
      em  = dbConn.getEntityManager();

      iso6392t  =
          ((TaalDto)  em.createNamedQuery(TaalDto.QRY_TAAL_ISO6391)
                        .setParameter(TaalDto.PAR_ISO6391,
                                      paramBundle.getString(PAR_TAAL))
                        .getSingleResult()).getIso6392t();

      getRangen();
      setSwitches();
      getTalen();

      latijnsenaam  = verwerkBestand();
    } catch (Exception e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
      return;
    }

    if (null != log) {
      try {
        log.close();
      } catch (BestandException e) {
        DoosUtils.foutNaarScherm(e.getLocalizedMessage());
      }
    }

    Collections.sort(talen);

    DoosUtils.naarScherm();
    DoosUtils.naarScherm(latijnsenaam);
    NatuurTools.printTotalen(
        String.format("%15s  %9s %9s %9s",
                      resourceBundle.getString(NatuurTools.LBL_RANGEN),
                      resourceBundle.getString(NatuurTools.LBL_AANTAL),
                      resourceBundle.getString(NatuurTools.LBL_UPDATE),
                      resourceBundle.getString(NatuurTools.LBL_NIEUW)),
        rangen, totalen);
    NatuurTools.printTotalen(
        String.format("%25s  %9s %9s %9s",
                      resourceBundle.getString(NatuurTools.LBL_TALEN),
                      resourceBundle.getString(NatuurTools.LBL_AANTAL),
                      resourceBundle.getString(NatuurTools.LBL_UPDATE),
                      resourceBundle.getString(NatuurTools.LBL_NIEUW)),
        talen, namen);
    klaar();
  }

  private static void addRang(String rang) {
    totalen.get(rang).addAantal();
  }

  private static void addTaal(String taal) {
    if (!namen.containsKey(taal)) {
      initTaal(taal);
    }

    namen.get(taal).addAantal();
  }

  private static void addNieuweRang(String rang) {
    totalen.get(rang).addNieuw();
  }

  private static void addNieuweTaal(String taal) {
    if (!namen.containsKey(taal)) {
      initTaal(taal);
    }

    namen.get(taal).addNieuw();
  }

  private static void addUpdateRang(String rang) {
    totalen.get(rang).addUpdate();
  }

  private static void addUpdateTaal(String taal) {
    if (!namen.containsKey(taal)) {
      initTaal(taal);
    }

    namen.get(taal).addUpdate();
  }

  private static void addTaxon(TaxonDto taxon) {
    if (readonly || !aanmaak ) {
      return;
    }

    if (!metondersoorten
        && taxon.getRang().equals(NatuurConstants.RANG_ONDERSOORT)) {
      return;
    }

    List<Message>  fouten  = TaxonValidator.valideer(taxon);

    if (fouten.isEmpty()) {
      em.getTransaction().begin();
      em.persist(taxon);
      em.getTransaction().commit();
      addNieuweRang(taxon.getRang());
    } else {
      printMessages(fouten);
    }
  }

  private static void addTaxonnaam(TaxonnaamDto taxonnaam) {
    if (readonly) {
      return;
    }

    List<Message>  fouten  = TaxonnaamValidator.valideer(taxonnaam);

    if (fouten.isEmpty()) {
      em.getTransaction().begin();
      em.persist(taxonnaam);
      em.getTransaction().commit();
      addNieuweTaal(taxonnaam.getTaal());
    } else {
      printMessages(fouten);
    }
  }

  private static void controleerHierarchie(TaxonDto taxon, TaxonDto parent,
                                           StringBuilder verandering) {
    if (null == taxon.getParentId()
        || taxon.getParentId().equals(parent.getTaxonId())
        || ONBEKEND.equals(taxon.getParentId())) {
      return;
    }

    var hierarchie  = getParent(taxon, parent.getRang());

    if (null == hierarchie.getTaxonId()) {
      printFout(MessageFormat.format(
                  resourceBundle.getString(ERR_STRUCTUUR),
                  prefix.get(taxon.getRang()) + "    ", parent.getRang()));

      return;
    }

    if (!parent.getRang().equals(hierarchie.getRang())) {
      return;
    }

    if (readonly) {
      printFout(MessageFormat.format(
                   resourceBundle.getString(ERR_STRUCTUUR),
                   prefix.get(taxon.getRang()) + "    ", parent.getRang()));
    } else {
      verandering.append(" parentId: ").append(taxon.getParentId())
                 .append(WORDT).append(parent.getTaxonId()).append(" ");
      taxon.setParentId(parent.getTaxonId());
    }
  }

  private static void controleerTaxon(TaxonDto taxon, Long volgnummer,
                                      TaxonDto parent, String status) {
    var verandering = new StringBuilder();

    controleerHierarchie(taxon, parent, verandering);

    if (readonly) {
      return;
    }

    if (hernummer && !volgnummer.equals(taxon.getVolgnummer())) {
      verandering.append(" volgnummer: ").append(taxon.getVolgnummer())
                 .append(WORDT).append(volgnummer);
      taxon.setVolgnummer(volgnummer);
    }
    if (!status.equals(taxon.getStatus())) {
      verandering.append(" status: ").append(taxon.getStatus())
                 .append(WORDT).append(status);
      taxon.setStatus(status);
    }

    if (!verandering.isEmpty()) {
      setTaxon(taxon, verandering);
    }
  }

  private static void controleerTaxonnamen(TaxonDto taxon,
                                           JSONObject taxonnamen) {
    for (var key : taxonnamen.keySet()) {
      var taal  = key.toString();
      if (isTaalValid(taal)
          && DoosUtils.isNotBlankOrNull(taxonnamen.get(taal))) {
        TaxonnaamDto  taxonnaamDto;
        if (taxon.hasTaxonnaam(taal)) {
          taxonnaamDto  = taxon.getTaxonnaam(taal);
          if (!taxonnaamDto.getNaam()
                           .equals(taxonnamen.get(taal))) {
            print(MessageFormat.format(
                      resourceBundle.getString(NatuurTools.MSG_VERSCHIL),
                      prefix.get(taxon.getRang()) + "    ", taal,
                      taxonnamen.get(taal), taxonnaamDto.getNaam()));
            taxonnaamDto.setNaam(taxonnamen.get(taal).toString());
            setTaxonnaam(taxonnaamDto);
          }
        } else {
          print(MessageFormat.format(
                    resourceBundle.getString(NatuurTools.MSG_NIEUW),
                    prefix.get(taxon.getRang()) + "    ", taal,
                    taxonnamen.get(taal)));
          taxonnaamDto  = new TaxonnaamDto();
          taxonnaamDto.setNaam(taxonnamen.get(taal).toString());
          taxonnaamDto.setTaal(taal);
          taxonnaamDto.setTaxonId(taxon.getTaxonId());
          addTaxonnaam(taxonnaamDto);
        }
        addTaal(taxonnaamDto.getTaal());
      }
    }

    taxon.getTaxonnamen().forEach(dto -> {
      if (!taxonnamen.containsKey(dto.getTaal())) {
        DoosUtils.foutNaarScherm(
            MessageFormat.format(
                resourceBundle.getString(NatuurTools.MSG_ONBEKEND),
                prefix.get(taxon.getRang()) + "    ", dto.getTaal(),
                dto.getNaam()));
      }
    });
  }

  private static void getAanwezigeTalen() {
    em.createNativeQuery(QRY_TALEN)
      .getResultList()
      .forEach(aanwezigetaal -> talen.add((String) aanwezigetaal));
  }

  private static TaxonDto getOnbekendeTaxon() {
    var taxon = new TaxonDto();

    taxon.setParentId(ONBEKEND);

    return taxon;
  }

  private static TaxonDto getParent(TaxonDto taxon, String parentRang) {
    var taxonRang = DoosUtils.nullToEmpty(taxon.getRang());

    if (rangen.indexOf(taxonRang) < rangen.indexOf(parentRang)) {
      return getOnbekendeTaxon();
    }

    if (rangen.indexOf(taxonRang) == rangen.indexOf(parentRang)) {
      return taxon;
    }

    return getParent(getTaxon(taxon.getParentId()), parentRang);
  }

  private static void getRangen() {
    var           taal      = paramBundle.getString(PAR_TAAL);
    List<RangDto> ranglijst =
        em.createQuery(NatuurTools.QRY_RANG).getResultList();

    ranglijst.forEach(rang -> {
      prefix.put(rang.getRang(),
                 DoosUtils.stringMetLengte("", rang.getNiveau().intValue()));
      rangen.add(rang.getRang());
      totalen.put(rang.getRang(), new Totalen(rang.getNaam(taal), 15));
    });
  }

  private static void getTalen() {
    if (talen.isEmpty()) {
      getAanwezigeTalen();
    }

    for (var iso6391 : talen) {
      initTaal(iso6391);
    }
  }

  private static TaxonDto getTaxon(Long taxonId) {
    try {
      return (TaxonDto) em.createQuery(String.format(QRY_TAXON, taxonId))
                          .getSingleResult();
    } catch (NoResultException e) {
      return getOnbekendeTaxon();
    }
  }

  private static TaxonDto getTaxon(String latijnsenaam, Long parentId,
                                   Long volgnummer, String rang,
                                   String status) {
    var query = em.createNamedQuery(TaxonDto.QRY_LATIJNSENAAM);
    query.setParameter(TaxonDto.PAR_LATIJNSENAAM, latijnsenaam);
    TaxonDto  resultaat;
    try {
      resultaat = (TaxonDto) query.getSingleResult();
      addRang(rang);
      printTaxon(rang, latijnsenaam);
    } catch (NoResultException e) {
      resultaat = new TaxonDto();
      resultaat.setLatijnsenaam(latijnsenaam);
      resultaat.setRang(rang);
      resultaat.setStatus(status);
      resultaat.setVolgnummer(volgnummer);
      if (aanmaak) {
        resultaat.setParentId(parentId);
        addTaxon(resultaat);
        addRang(rang);
        printTaxon(rang, latijnsenaam);
      } else {
        resultaat.setParentId(ONBEKEND);
        printFout(
          MessageFormat.format(
              resourceBundle.getString(
                NatuurTools.MSG_AFWEZIG), prefix.get(rang),
                DoosUtils.stringMetLengte(rang, 3, " "), latijnsenaam));
      }
    }

    return resultaat;
  }

  protected static void initTaal(String taal) {
    var taalnaam  = taal;

    try {
      taalnaam    =
          ((TaalDto)  em.createNamedQuery(TaalDto.QRY_TAAL_ISO6392T)
                        .setParameter(TaalDto.PAR_ISO6392T, taal)
                        .getSingleResult()).getTaalnaam(iso6392t).getNaam();

    } catch(ObjectNotFoundException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }

    namen.put(taal,
              new Totalen(String.format("%s (%s)", taalnaam, taal), 25));

    if (!talen.contains(taal)) {
      talen.add(taal);
    }
  }

  protected static boolean isTaalValid(String taal) {
    return !talenParameter || talen.contains(taal);
  }

  protected static void print(String regel) {
    if (null != log) {
      try {
        log.write(regel);
      } catch (BestandException e) {
        // Jammer maar helaas :-)
      }
    }

    if (!stil) {
      DoosUtils.naarScherm(regel);
    }
  }

  protected static void printFout(String regel) {
    if (null != log) {
      try {
        log.write(regel);
      } catch (BestandException e) {
        // Jammer maar helaas :-)
      }
    }

    if (!stil) {
      DoosUtils.foutNaarScherm(regel);
    }
  }

  protected static void printMessages(List<Message> fouten) {
    fouten.forEach(fout ->
      DoosUtils.foutNaarScherm(getMelding(LBL_FOUT, fout.toString())));
  }

  protected static void printTaxon(String rang, String latijnsenaam) {
    print(String.format("%s%-3s %s",  prefix.get(rang), rang, latijnsenaam));
  }

  private static void setSwitches() {
    stil            = paramBundle.getBoolean(NatuurTools.PAR_STIL);

    if (Boolean.TRUE.equals(paramBundle.getBoolean(PAR_READONLY))) {
      readonly      = true;
      return;
    }

    aanmaak         = paramBundle.getBoolean(NatuurTools.PAR_AANMAAK);
    hernummer       = paramBundle.getBoolean(NatuurTools.PAR_HERNUMMER);
    metondersoorten = paramBundle.getBoolean(NatuurTools.PAR_METONDERSOORT);

    DoosUtils.naarScherm();
    DoosUtils.naarScherm(resourceBundle.getString(NatuurTools.MSG_WIJZIGEN));
    if (aanmaak) {
      DoosUtils.naarScherm(resourceBundle
                              .getString(NatuurTools.MSG_AANMAKEN));
    }
    if (hernummer) {
      DoosUtils.naarScherm(resourceBundle
                              .getString(NatuurTools.MSG_HERNUMMER));
    }
    if (metondersoorten) {
      DoosUtils.naarScherm(resourceBundle
                              .getString(NatuurTools.MSG_METONDERSOORTEN));
    }
    DoosUtils.naarScherm();
  }

  private static void setTaxon(TaxonDto taxon, StringBuilder verandering) {
    if (readonly || taxon.getParentId().equals(ONBEKEND)) {
      return;
    }

    var form  = new Taxon(taxon);
    if (null == taxon.getParent()) {
      var latijnsenaam  = taxon.getLatijnsenaam();
      var einde         = latijnsenaam.contains(" ")
                              ? latijnsenaam.lastIndexOf(" ") :
                                latijnsenaam.length();

      form.setParentLatijnsenaam(latijnsenaam.substring(0, einde));
    }

    List<Message>  fouten  = TaxonValidator.valideer(form);

    if (fouten.isEmpty()) {
      em.getTransaction().begin();
      TaxonDto  updated = em.merge(taxon);
      em.persist(updated);
      em.getTransaction().commit();
      print(MessageFormat.format(
                    resourceBundle.getString(NatuurTools.MSG_WIJZIGING),
                    prefix.get(taxon.getRang()) + "    ",
                    resourceBundle.getString(NatuurTools.MSG_HIERARCHIE),
                    verandering.toString().trim()));
      addUpdateRang(taxon.getRang());
    } else {
      printMessages(fouten);
    }
  }

  private static void setTaxonnaam(TaxonnaamDto taxonnaam) {
    if (readonly) {
      return;
    }

    var fouten  = TaxonnaamValidator.valideer(taxonnaam);

    if (fouten.isEmpty()) {
      em.getTransaction().begin();
      TaxonnaamDto  updated = em.merge(taxonnaam);
      em.persist(updated);
      em.getTransaction().commit();
      addUpdateTaal(taxonnaam.getTaal());
    } else {
      printMessages(fouten);
    }
  }

  private static String verwerkBestand() {
    var latijnsenaam  = "?";

    try (var jsonBestand   =
          new JsonBestand.Builder()
                         .setBestand(
                             paramBundle.getBestand(NatuurTools.PAR_JSON,
                                                    BestandConstants.EXT_JSON))
                         .setCharset(paramBundle.getString(PAR_CHARSETIN))
                         .build()) {
      latijnsenaam    = jsonBestand.get(NatuurTools.KEY_LATIJN).toString();
      var   rang      = jsonBestand.get(NatuurTools.KEY_RANG).toString();

      var   parent    = getTaxon(latijnsenaam, 0L, 0L, rang, "");
      if (null == parent.getTaxonId()) {
        return latijnsenaam;
      }

      for (Object taxa :
              (JSONArray) jsonBestand.get(NatuurTools.KEY_SUBRANGEN)) {
        verwerkRang(parent, (JSONObject) taxa);
      }
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    }

    return latijnsenaam;
  }

  private static void verwerkRang(TaxonDto parent, JSONObject json) {
    var latijnsenaam  = json.get(NatuurTools.KEY_LATIJN).toString();
    var rang          = json.get(NatuurTools.KEY_RANG).toString();
    var status        = "";
    if (json.containsKey(NatuurTools.KEY_STATUS)) {
      status          = json.get(NatuurTools.KEY_STATUS).toString();
    }
    var volgnummer    =
        Long.valueOf(json.get(NatuurTools.KEY_SEQ).toString());

    TaxonDto  taxon   = getTaxon(latijnsenaam, parent.getTaxonId(),
                                 volgnummer, rang, status);
    controleerTaxon(taxon, volgnummer, parent, status);

    if (null == taxon.getTaxonId()) {
      return;
    }

    if (json.containsKey(NatuurTools.KEY_NAMEN)) {
      controleerTaxonnamen(taxon, (JSONObject) json.get(NatuurTools.KEY_NAMEN));
    }
    if (json.containsKey(NatuurTools.KEY_SUBRANGEN)) {
      for (var subrang : (JSONArray) json.get(NatuurTools.KEY_SUBRANGEN)) {
        verwerkRang(taxon, (JSONObject) subrang);
      }
    }
  }
}
