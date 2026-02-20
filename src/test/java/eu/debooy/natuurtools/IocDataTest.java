/*
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

import eu.debooy.doosutils.access.Bestand;
import eu.debooy.doosutils.exception.BestandException;
import eu.debooy.doosutils.test.BatchTest;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * @author Marco de Booij
 */
public class IocDataTest extends BatchTest {
  protected static final  ClassLoader CLASSLOADER =
      IocDataTest.class.getClassLoader();

  private static final  String  BST_CSV     = "MultilingIOC.csv";
  private static final  String  BST_JSON    = "MultilingIOC.json";
  private static final  String  BST_MASTER  = "MasterIOC.csv";

  @AfterClass
  public static void afterClass() {
    verwijderBestanden(getTemp() + File.separator,
                       new String[] {BST_CSV, BST_JSON, BST_MASTER});
  }

  @BeforeClass
  public static void beforeClass() throws BestandException {
    Locale.setDefault(new Locale.Builder().setLanguage("nl").build());
    resourceBundle  = ResourceBundle.getBundle("ApplicatieResources",
                                               Locale.getDefault());

    try {
      kopieerBestand(CLASSLOADER, BST_CSV,
                     getTemp() + File.separator + BST_CSV);
      kopieerBestand(CLASSLOADER, BST_MASTER,
                     getTemp() + File.separator + BST_MASTER);
    } catch (IOException e) {
      System.out.println(e.getLocalizedMessage());
      throw new BestandException(e);
    }
  }

  protected void execute(String[] args) {
    before();
    IocData.execute(args);
    after();
  }

  @Test
  public void testCsv() throws BestandException {
    var args  = new String[] {
      "--" + NatuurTools.PAR_IOCNAMEN + "=" + getTemp() + File.separator
           + BST_CSV,
      "--" + NatuurTools.PAR_IOCSTRUCTUUR + "=" + getTemp() + File.separator
           + BST_MASTER,
      "--" + NatuurTools.PAR_TALEN + "=en,ca,zh,z,hr,cs,da,nl,fi,fr,de,it,ja,lt,no,pl,pt,ru,sr,sk,es,sv,tr,uk,af,et,hu,is,id,lv,se,sl,th",
      "--taal" + "=en"};

    execute(args);

    assertEquals("15", out.get(16).split(":")[1].trim());
    assertEquals("2",  out.get(17).split(":")[1].trim());
    assertEquals("3",  out.get(18).split(":")[1].trim());
    assertEquals("4",  out.get(19).split(":")[1].trim());
    assertEquals("6",  out.get(20).split(":")[1].trim());
    assertTrue(
        Bestand.equals(
            Bestand.openInvoerBestand(getTemp() + File.separator
                                      + BST_JSON),
            Bestand.openInvoerBestand(CLASSLOADER, BST_JSON)));
  }

  @Test
  public void testLeeg() {
    var args  = new String[] {};

    execute(args);

    assertEquals(1, err.size());
  }
}
